# SSH 终端会话设计文档

> 本文档记录 SSH 终端会话（交互式 Shell）的实现架构、接口契约与设计细节。
> 本次实现为最新提交，文档正在完善中。

---

## 1. 领域概述

SSH 终端会话基于已建立的 SSH 连接，提供**交互式 Shell 通道**的能力：

- 打开/关闭终端会话（ChannelShell）
- 提供终端 I/O：写入输入、读取输出、调整窗口大小
- 支持 Agent 专用捕获模式，隔离前端轮询与 Agent 命令输出
- 支持单条命令执行（execCommand），等待完成后返回完整输出

一个 `connectionId` 在同一时刻只允许一个活跃终端会话。打开新会话时会自动关闭旧会话。

---

## 2. 模块依赖关系

```
Trigger 层
  SshTerminalController ──→ ISshTerminalDomainService

Domain 层
  ISshTerminalDomainService ──→ ISshSessionPort（SSH 传输层）
                              ITerminalSessionPort（Shell 通道管理）
  TerminalSessionEntity      ← 内存聚合根，管理会话生命周期

Infrastructure 层
  TerminalSessionPort ──→ ISshSessionPort（获取 JSch Session）
                        ──→ JSch ChannelShell（Shell 通道）
                        ──→ daemon 读取线程（持续读取输出到缓冲区）
```

---

## 3. 已实现内容

### 3.1 API 层

| 接口/Controller | 路径 | 方法 | 说明 |
|---|---|---|---|
| `ISshTerminalService` | — | — | 终端会话服务接口 |
| `SshTerminalController` | `POST /api/v1/ssh/terminal/open` | openTerminal | 打开终端会话，返回 sessionId + 初始输出 |
| — | `POST /api/v1/ssh/terminal/exec` | execCommand | 执行单条命令，等待完成后返回完整输出 |
| — | `POST /api/v1/ssh/terminal/write` | writeToTerminal | 写入原始输入（逐字节模式） |
| — | `GET  /api/v1/ssh/terminal/read` | readFromTerminal | 轮询读取终端输出缓冲区 |
| — | `POST /api/v1/ssh/terminal/resize` | resizeTerminal | 调整终端窗口大小 |
| — | `POST /api/v1/ssh/terminal/close` | closeTerminal | 关闭终端会话 |

### 3.2 Domain 层

| 类 | 职责 |
|---|---|
| `ISshTerminalDomainService` | 终端领域服务接口，定义 open/exec/resize/read/write/close 等用例 |
| `ITerminalSessionPort` | 终端会话出站端口，定义 Shell 通道的读写和管理接口 |
| `TerminalSessionEntity` | 终端会话聚合根，管理 sessionId、cols/rows、状态、活跃时间 |
| `SshTerminalService` | 终端领域服务实现，编排 ITerminalSessionPort，维护 sessionCache，实现命令执行等待逻辑 |

### 3.3 Infrastructure 层

| 类 | 职责 |
|---|---|
| `TerminalSessionPort` | Shell 通道管理器，管理 ChannelShell 的生命周期、I/O 流、输出缓冲区、Agent 捕获模式、读取线程 |

---

## 4. 设计细节

### 4.1 为什么用 HTTP 轮询而非 WebSocket

这是一个 Tauri 桌面应用，前后端都在本地运行，网络延迟极低（通常 < 1ms）。在这种场景下：

- **HTTP 轮询完全够用**：50ms 的轮询间隔对终端操作来说感知不到延迟
- **实现更简单**：不需要维护 WebSocket 连接状态、心跳、重连机制
- **无额外依赖**：不需要引入 WebSocket 服务器和客户端库
- **足够灵活**：前端可以根据需要调整轮询频率，不影响后端

因此选择 HTTP 轮询方案，而非 WebSocket。

### 4.2 为什么需要逐字节模式（writeToTerminal）

终端交互不是"敲完一整行命令再发送"的。像以下这些交互，必须立即发到 Shell 才能响应：

- **方向键上下翻历史**：单个 `\u001b[A` / `\u001b[B`
- **Tab 补全**：单个 `\t`
- **Ctrl+C 中断**：单个 `\u0003`
- **Ctrl+D 注销**：单个 `\u0004`

这些操作都是用户按键后立即发送，不等回车。如果等用户敲完回车再发，这些交互就全废了。因此 `writeToTerminal` 接口设计为**逐字节模式**，每次按键立即写入 Shell 通道。

### 4.3 终端会话生命周期

```
openTerminal(connectionId, cols, rows)
  ├── 校验 SSH 连接已建立
  ├── 清理同 connectionId 的旧会话（内存缓存 + 基础设施层 channel）
  ├── TerminalSessionPort.openTerminal()
  │     ├── 获取 JSch Session
  │     ├── 打开 ChannelShell，设置 pty
  │     ├── 启动 daemon 读取线程
  │     └── 等待首数据到达（最多 3s）+ 额外 200ms 让 MOTD 积累
  ├── 创建 TerminalSessionEntity，写入 sessionCache
  └── 返回 entity

write(sessionId, command)
  └── TerminalSessionPort.write()
        └── OutputStream.write()，失败重试 2 次

read(sessionId)
  └── TerminalSessionPort.read()
        ├── 非阻塞：直接 drain 缓冲区当前内容
        └── 若读取线程已退出且 channel 仍连接，尝试重启读取线程

execCommand(sessionId, command)
  ├── 校验 session 活跃
  ├── 开启 Agent 捕获模式（agentCaptureMode = true）
  ├── 清空 agentBuffer 残留
  ├── write(command + "\n")
  ├── 轮询 agentBuffer，最多 waitTimeoutMs
  │     ├── 每 100ms 检查一次
  │     ├── 检测 prompt 特征（# 或 $ 后跟空白/换行，或多行内容含 \r\n）判定命令完成
  │     └── 3 次空读 + prompt 特征 → 退出轮询
  └── 关闭 Agent 捕获模式

resizeTerminal / closeTerminal / sessionExists
  └── 直接委托 TerminalSessionPort
```

### 4.4 Agent 捕获模式

前端通过 `read` 接口轮询获取输出，Agent 通过 `execCommand` 执行命令。两者共享同一个 Shell 通道的输出流，但通过双缓冲隔离：

- `outputBuffer`：所有 Shell 输出都写入此缓冲区，前端 `read()` drain 它
- `agentBuffer`：仅当 `agentCaptureMode = true` 时，输出同时写入此缓冲区
- `execCommand` 开启捕获 → 清空 agentBuffer → 执行命令 → 轮询 agentBuffer → 关闭捕获

这样可以避免前端轮询"偷走" Agent 命令的输出，导致 Agent 拿不到完整结果。

### 4.5 命令完成检测策略

`execCommand` 通过以下条件判定命令执行完成（满足任一即可退出轮询）：

```
1. 连续 3 次空读 agentBuffer 且已收集到输出
2. 输出匹配 prompt 特征：
   - 以 # 或 $ 开头后跟空白/换行行尾
   - 或输出包含多行 \r\n（超过 2 行）
```

默认等待超时：5000ms，检查间隔：100ms。

### 4.6 写入重试策略

`TerminalSessionPort.write()` 对 I/O 失败自动重试 2 次，每次间隔 50ms。
重试原因：Shell 通道临时 I/O 异常（非断连），重试通常可恢复。

### 4.7 读取线程容错

daemon 读取线程对以下异常有容错处理：

- `SocketTimeoutException`：非断连，忽略并继续循环
- `IOException`（连续 < 3 次且 channel 仍连接）：短暂等待后重试
- `IOException`（连续 ≥ 3 次 或 channel 已断开）：退出线程

线程退出后，`read()` 方法若发现 channel 仍连接，会尝试重启读取线程。

### 4.8 初始输出处理

`openTerminal` 在基础设施层等待首数据到达（最多 3s）+ 额外 200ms，
Controller 的 `waitForInitialOutput` 再 drain 一次缓冲区，确保 MOTD/prompt 完整。
前端 `open` 接口直接返回 `initialOutput`，不再依赖轮询获取初始状态。

---

## 5. 接口契约

### 5.1 openTerminal

```
POST /api/v1/ssh/terminal/open
Request:  { "connectionId": "xxx", "cols": 120, "rows": 24 }
Response: { "sessionId": "xxx", "connectionId": "xxx", "initialOutput": "Last login..." }
```

- `cols`/`rows` 可选，默认 120×24
- 若 `connectionId` 不存在或未连接，返回 ILLEGAL_PARAMETER

### 5.2 execCommand

```
POST /api/v1/ssh/terminal/exec
Request:  { "sessionId": "xxx", "command": "ls -la" }
Response: { "output": "total 32\ndrwxr-xr-x..." }
```

- 同步阻塞，等待命令完成或超时
- 若 `sessionId` 不存在，返回 ILLEGAL_PARAMETER

### 5.3 writeToTerminal

```
POST /api/v1/ssh/terminal/write
Request:  { "sessionId": "xxx", "input": "\u0003" }  // Ctrl+C
Response: { "code": "0", "info": "success" }
```

- 原始字节输入，不由 Shell 解析 echo
- 用于方向键、Tab、Ctrl 组合键等即时交互

### 5.4 readFromTerminal

```
GET /api/v1/ssh/terminal/read?sessionId=xxx
Response: { "output": "drwxr-xr-x  3 user user 4096 ..." }
```

- 非阻塞，返回缓冲区当前内容（可能为空字符串）
- 前端应定期轮询（建议 50-200ms 间隔，本地场景 50ms 足够）

### 5.5 resizeTerminal

```
POST /api/v1/ssh/terminal/resize
Request:  { "sessionId": "xxx", "cols": 80, "rows": 24 }
Response: { "code": "0", "info": "success" }
```

### 5.6 closeTerminal

```
POST /api/v1/ssh/terminal/close?sessionId=xxx
Response: { "code": "0", "info": "success" }
```

---

## 6. 已知限制

- 一个 `connectionId` 同一时刻只允许一个活跃终端会话，打开新会话会强制关闭旧会话
- `TerminalSessionEntity` 仅存在于内存（`ConcurrentHashMap`），应用重启后所有终端会话丢失
- `read()` 重启读取线程无次数上限，理论上可无限重启
- 无空闲超时自动清理机制，不活跃的会话会一直占用 JSch ChannelShell 资源
- `execCommand` 的 prompt 检测基于简单正则，对非常规 prompt（如自定义 PS1、Windows cmd）可能误判
- 不支持伪终端输出中的 ANSI 转义序列过滤，由前端 xterm.js 自行处理

---

## 7. 待改进点

| 优先级 | 改进项 | 说明 |
|---|---|---|
| P1 | 正则 prompt 检测 `$` 未转义 | `SshTerminalService.java:127,258` 的 `[#$]` 应改为 `[#\\$]`，否则永远匹配不到 `$` prompt |
| P1 | read() 重启次数上限 | 当前无上限，建议 3 次后标记会话失效，避免无限循环重启浪费资源 |
| P2 | Session 空闲超时自动清理 | 建议 30 分钟无活跃自动清理，或增加后台定期扫描任务 |
| P2 | TerminalSessionEntity 持久化 | 当前纯内存，应用重启丢失。如需会话恢复，需新增表 + DAO + Repository |
| P3 | 前端交互测试 | 需要前端 xterm.js 联调验证 write/read/resize 的时序和编码正确性 |
| P4 | 命令执行超时可配置 | 当前固定 5000ms，建议通过 DTO 参数或连接配置项传入 |

---

## 8. 测试约定

SSH 终端相关测试位置：

| 测试类型 | 位置 | 说明 |
|---|---|---|
| 离线单元测试 | `test/domain/ssh/SshTerminalServiceTest.java` | Mock ITerminalSessionPort，验证命令执行等待逻辑、session 校验 |
| 手动集成测试 | `test/infrastructure/ssh/TerminalSessionPortManualTest.java` | 依赖真实 SSH 服务器，验证 Shell 通道 I/O |
| Controller 单测 | `test/trigger/http/SshTerminalControllerTest.java` | Mock DomainService，验证接口响应码和 DTO 转换 |

---

## 9. 相关文档

- `doc/README.md` — 项目文档索引
- `doc/01-项目规范与架构约束.md` — DDD 分层、命名规范、注入规范
- `doc/sql/ssh.sql` — SSH 连接表结构
- `doc/sql/ssh_config.sql` — SSH 连接配置表结构
- `doc/ssh/01-SSH-连接管理设计文档.md` — SSH 连接管理实现
