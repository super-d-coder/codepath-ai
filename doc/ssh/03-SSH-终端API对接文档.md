# SSH 终端 API 对接文档

> 面向对象：前端工程师、接口联调人员。
>
> 文档性质：当前后端代码的接口契约。本文只描述数据与调用方式，不约束前端页面、组件、交互或样式设计。

---

## 1. 基础信息

| 项目 | 值 |
|---|---|
| 本地默认服务地址 | `http://127.0.0.1:8091` |
| API 前缀 | `/api/v1/ssh/terminal` |
| 响应格式 | `application/json` |
| 字符编码 | UTF-8 |
| 当前鉴权 | 无 |

前端工程可通过环境变量覆盖后端地址：

```text
VITE_API_BASE_URL=http://127.0.0.1:8091
```

注意：
- 本模块接口依赖已建立的 SSH 连接；调用终端接口前，需先通过 `/api/v1/ssh` 下的连接管理接口建立连接
- 当前没有登录鉴权，部署到公网前应由网关或后续权限模块限制访问
- 后端 Controller 当前允许跨域；Vite 开发环境也可以代理 `/api` 到后端

---

## 2. 统一响应结构

所有接口使用统一响应体：

```ts
export interface ApiResponse<T> {
  code: string;
  info: string;
  data?: T;
}
```

成功示例：

```json
{
  "code": "0000",
  "info": "成功",
  "data": { ... }
}
```

常用顶层状态码：

| code | 含义 |
|---|---|
| `0000` | 后端已正常处理请求 |
| `0001` | 未知失败 |
| `0002` | 非法参数 |

---

## 3. TypeScript 数据类型

### 3.1 打开终端会话

```ts
export interface TerminalOpenRequestDTO {
  connectionId: string;
  cols?: number;   // 默认 120
  rows?: number;   // 默认 24
}

export interface TerminalOpenResponseDTO {
  sessionId: string;
  connectionId: string;
  initialOutput: string;   // 连接后的欢迎信息 / MOTD / prompt
}
```

### 3.2 执行命令

```ts
export interface TerminalExecRequestDTO {
  sessionId: string;
  command: string;
}

export interface TerminalExecResponseDTO {
  output: string;   // 命令执行后的完整终端输出
}
```

### 3.3 写入原始输入

```ts
export interface TerminalWriteRequestDTO {
  sessionId: string;
  input: string;    // 原始按键数据，如 "\u0003"（Ctrl+C）、"\u001b[A"（方向键上）
}
```

### 3.4 读取终端输出

```ts
export interface TerminalReadResponseDTO {
  output: string;   // 终端输出内容，可能为空字符串
}
```

### 3.5 调整终端大小

```ts
export interface TerminalResizeRequestDTO {
  sessionId: string;
  cols: number;
  rows: number;
}
```

### 3.6 时间与状态字段

本模块接口不涉及持久化时间字段，所有状态均为内存实时状态。

---

## 4. 打开终端会话

### 4.1 请求

```http
POST /api/v1/ssh/terminal/open
Content-Type: application/json
```

请求体：

```json
{
  "connectionId": "abc123",
  "cols": 120,
  "rows": 24
}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `connectionId` | string | 是 | — | 已建立的 SSH 连接 ID |
| `cols` | integer | 否 | 120 | 终端列数 |
| `rows` | integer | 否 | 24 | 终端行数 |

### 4.2 响应

```json
{
  "code": "0000",
  "info": "成功",
  "data": {
    "sessionId": "session_001",
    "connectionId": "abc123",
    "initialOutput": "Last login: Wed Aug 30 10:00:00 2026\r\nWelcome to Ubuntu 22.04\r\nuser@host:~$ "
  }
}
```

说明：
- `sessionId` 是后续所有终端操作的唯一标识，前端需妥善保存
- `initialOutput` 包含连接后的欢迎信息（MOTD / Last login / 初始 prompt），前端应直接显示在终端区域，无需额外轮询
- 若 `connectionId` 不存在或未连接，返回 `ILLEGAL_PARAMETER`

---

## 5. 写入原始输入（逐字节模式）

### 5.1 请求

```http
POST /api/v1/ssh/terminal/write
Content-Type: application/json
```

请求体：

```json
{
  "sessionId": "session_001",
  "input": "\u0003"
}
```

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `sessionId` | string | 是 | 终端会话 ID |
| `input` | string | 是 | 原始输入内容，通常为单个字符或转义序列 |

### 5.2 响应

```json
{
  "code": "0000",
  "info": "成功"
}
```

说明：
- 本接口为**逐字节模式**：用户每次按键（包括方向键、Tab、Ctrl 组合键）都应立即调用本接口
- 常见按键映射：

| 操作 | input 值 |
|---|---|
| Ctrl+C | `"\u0003"` |
| Ctrl+D | `"\u0004"` |
| Tab | `"\t"` |
| 方向键上 | `"\u001b[A"` |
| 方向键下 | `"\u001b[B"` |
| 方向键右 | `"\u001b[C"` |
| 方向键左 | `"\u001b[D"` |
| 回车 | `"\r"` 或 `"\n"` |

- 普通字符直接传入字符本身，如 `"l"`、`"s"`、`"-"`、`" "` 等
- 本接口不返回终端输出，输出需通过 `read` 接口轮询获取

---

## 6. 轮询读取终端输出

### 6.1 请求

```http
GET /api/v1/ssh/terminal/read?sessionId=session_001
```

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `sessionId` | string | 是 | 终端会话 ID |

### 6.2 响应

```json
{
  "code": "0000",
  "info": "成功",
  "data": {
    "output": "user@host:~$ ls -la\r\ntotal 32\r\ndrwxr-xr-x  3 user user 4096 ..."
  }
}
```

说明：
- 本接口为**非阻塞**：立即返回缓冲区当前内容，后端不等待新数据
- 无新输出时返回 `output: ""`（空字符串，不是 `null`）
- 前端应定期轮询，建议间隔 **50–200ms**；本模块为 Tauri 桌面应用本地调用，50ms 足够
- 返回内容包含原始 `\r\n`，前端 xterm.js 可直接处理
- 返回内容可能包含 ANSI 转义序列（颜色、光标移动等），前端应交给 xterm.js 解析，不应在业务层过滤

---

## 7. 执行单条命令（阻塞）

### 7.1 请求

```http
POST /api/v1/ssh/terminal/exec
Content-Type: application/json
```

请求体：

```json
{
  "sessionId": "session_001",
  "command": "ls -la"
}
```

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `sessionId` | string | 是 | 终端会话 ID |
| `command` | string | 是 | 要执行的命令（不含末尾换行，后端自动追加） |

### 7.2 响应

```json
{
  "code": "0000",
  "info": "成功",
  "data": {
    "output": "total 32\r\ndrwxr-xr-x  3 user user 4096 ...\r\nuser@host:~$ "
  }
}
```

说明：
- 本接口为**同步阻塞**：后端等待命令执行完成或超时后返回
- 默认超时时间：5000ms；超时后返回已收集到的部分输出
- 返回的输出包含命令执行结果和最终 prompt（以 `#` 或 `$` 结尾）
- 本接口内部开启 Agent 捕获模式，与前端 `read` 轮询隔离，不会互相干扰输出
- 适合 Agent 或脚本场景使用；用户手动交互场景应使用 `write` + `read` 组合

---

## 8. 调整终端窗口大小

### 8.1 请求

```http
POST /api/v1/ssh/terminal/resize
Content-Type: application/json
```

请求体：

```json
{
  "sessionId": "session_001",
  "cols": 80,
  "rows": 24
}
```

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `sessionId` | string | 是 | 终端会话 ID |
| `cols` | integer | 是 | 新的列数 |
| `rows` | integer | 是 | 新的行数 |

### 8.2 响应

```json
{
  "code": "0000",
  "info": "成功"
}
```

说明：
- 前端在终端组件大小变化（如拖拽调整窗口、切换全屏）时应调用本接口
- 后端通过 `ChannelShell.setPtySize()` 通知远端更新终端尺寸

---

## 9. 关闭终端会话

### 9.1 请求

```http
POST /api/v1/ssh/terminal/close?sessionId=session_001
```

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `sessionId` | string | 是 | 终端会话 ID |

### 9.2 响应

```json
{
  "code": "0000",
  "info": "成功"
}
```

说明：
- 关闭后 `sessionId` 失效，后续所有使用该 `sessionId` 的请求将返回 `ILLEGAL_PARAMETER`
- 前端在组件卸载或用户主动关闭终端时应调用本接口
- 若后端异常退出，前端应假定 `sessionId` 已失效，重新 `openTerminal`

---

## 10. 完整交互流程示例

```
1. 打开会话
   POST /api/v1/ssh/terminal/open
   → 获得 sessionId + initialOutput，前端直接显示 initialOutput

2. 用户输入 "ls -la" 并回车
   POST /api/v1/ssh/terminal/write  { "sessionId": "xxx", "input": "l" }
   POST /api/v1/ssh/terminal/write  { "sessionId": "xxx", "input": "s" }
   POST /api/v1/ssh/terminal/write  { "sessionId": "xxx", "input": " " }
   POST /api/v1/ssh/terminal/write  { "sessionId": "xxx", "input": "-" }
   POST /api/v1/ssh/terminal/write  { "sessionId": "xxx", "input": "l" }
   POST /api/v1/ssh/terminal/write  { "sessionId": "xxx", "input": "a" }
   POST /api/v1/ssh/terminal/write  { "sessionId": "xxx", "input": "\r" }
   同时启动轮询：
   GET /api/v1/ssh/terminal/read?sessionId=xxx   (每 50ms)

3. 用户按方向键上翻历史
   POST /api/v1/ssh/terminal/write  { "sessionId": "xxx", "input": "\u001b[A" }
   同时继续轮询 read

4. 用户调整终端大小
   POST /api/v1/ssh/terminal/resize  { "sessionId": "xxx", "cols": 80, "rows": 24 }

5. Agent 执行一条命令（不与前端交互混用）
   POST /api/v1/ssh/terminal/exec  { "sessionId": "xxx", "command": "pwd" }
   → 阻塞返回完整输出

6. 关闭会话
   POST /api/v1/ssh/terminal/close?sessionId=xxx
```

---

## 11. 前端调用参考

以下代码只展示接口调用，不规定前端状态管理或页面设计：

```ts
const API_BASE_URL = (
  import.meta.env.VITE_API_BASE_URL ?? "http://127.0.0.1:8091"
).replace(/\/$/, "");

interface ApiResponse<T> {
  code: string;
  info: string;
  data?: T;
}

async function request<T>(
  path: string,
  init?: RequestInit
): Promise<T> {
  const url = `${API_BASE_URL}/api/v1/ssh/terminal${path}`;
  const response = await fetch(url, init);
  const payload = (await response.json()) as ApiResponse<T>;

  if (!response.ok || String(payload.code) !== "0000") {
    throw new Error(payload.info || `请求失败（HTTP ${response.status}）`);
  }

  return payload.data as T;
}

export const sshTerminalApi = {
  open(connectionId: string, cols = 120, rows = 24) {
    return request<TerminalOpenResponseDTO>("/open", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ connectionId, cols, rows }),
    });
  },

  exec(sessionId: string, command: string) {
    return request<TerminalExecResponseDTO>("/exec", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ sessionId, command }),
    });
  },

  write(sessionId: string, input: string) {
    return request<void>("/write", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ sessionId, input }),
    });
  },

  read(sessionId: string) {
    return request<TerminalReadResponseDTO>(`/read?sessionId=${encodeURIComponent(sessionId)}`);
  },

  resize(sessionId: string, cols: number, rows: number) {
    return request<void>("/resize", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ sessionId, cols, rows }),
    });
  },

  close(sessionId: string) {
    return request<void>(`/close?sessionId=${encodeURIComponent(sessionId)}`, {
      method: "POST",
    });
  },
};
```

轮询示例（供参考）：

```ts
let pollTimer: number | null = null;

function startPolling(sessionId: string, onOutput: (text: string) => void) {
  pollTimer = window.setInterval(async () => {
    try {
      const { output } = await sshTerminalApi.read(sessionId);
      if (output) onOutput(output);
    } catch (e) {
      console.error("轮询读取失败", e);
      stopPolling();
    }
  }, 50);
}

function stopPolling() {
  if (pollTimer !== null) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
}
```

---

## 12. 错误处理建议

| 场景 | 前端可观察结果 | 建议 |
|---|---|---|
| `sessionId` 不存在或已关闭 | `code="0002"`，info 描述原因 | 提示"会话已失效"，重新 openTerminal |
| `connectionId` 未连接 | `openTerminal` 返回 `ILLEGAL_PARAMETER` | 提示"请先建立 SSH 连接" |
| 后端未启动或地址错误 | `fetch` 网络异常 | 提示检查后端和 `VITE_API_BASE_URL` |
| 后端返回非 JSON | JSON 解析异常 | 提示服务响应无效并记录 HTTP 状态 |
| `write` 失败 | `code != "0000"` | 记录错误，必要时重试一次 |
| 终端断开（网络/远端退出） | `read` 返回断开提示或空输出 | 提示连接已断开，清理 session |
| 后端重启 | 所有请求网络异常或 session 失效 | 前端应检测到异常，引导用户重新打开终端 |

---

## 13. 后端过程日志

后端终端接口会在控制台输出可检索的简短过程日志，便于确认请求是否真正进入后端：

```text
打开终端会话 connectionId=xxx cols=120 rows=24
终端会话打开成功 sessionId=xxx
Agent执行命令 sessionId=xxx command=ls -la
命令执行完成 sessionId=xxx outputLength=256
关闭终端会话 sessionId=xxx
终端会话已关闭 sessionId=xxx
```

外部请求重试时会输出来源、当前次数和最大次数；日志不会输出密码、私钥或完整授权头。

---

## 14. 后端接口变更约定

若后续变更以下内容，后端开发人员必须同步更新本文：

- API 路径或 HTTP 方法；
- 请求参数、默认值或边界；
- DTO 字段或可空性；
- 顶层状态码；
- 轮询建议间隔；
- 命令执行超时时间；
- 字符编码或换行符约定。
