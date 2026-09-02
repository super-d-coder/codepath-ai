# SSH 连接管理设计文档

> 本文档记录 SSH 连接管理模块的实现架构、接口契约与设计决策。
> 对应代码提交：cb4b946 → bea1215 → dffd8e7 → 7befa9b

---

## 1. 领域概述

SSH 连接管理负责 SSH 连接的**配置持久化**和**传输层生命周期**：

- 创建/更新/删除连接配置（存储在 MySQL）
- 建立/断开 JSch Session
- 连接状态同步与查询
- 密码/私钥加密存储

连接配置与实时传输层分离：`SshConnectionEntity` 是持久化记录，`Session` 是内存中的传输通道。

---

## 2. 模块依赖关系

```
Trigger 层
  SshConnectionController ──→ ISshConnectionDomainService

Domain 层
  ISshConnectionDomainService ──→ ISshConnectionRepository（持久化）
                                 ISshSessionPort（传输层）

Infrastructure 层
  SshConnectionRepository     ──→ ISshConnectionDAO（MyBatis）
                                 ISshConnectionConfigDAO
                                 PasswordEncryptor
  SshSessionPort              ──→ JSch（传输层）
```

---

## 3. 已实现内容

### 3.1 API 层

| 接口/Controller | 路径 | 方法 | 说明 |
|---|---|---|---|
| `ISshConnectionService` | — | — | 连接管理服务接口 |
| `SshConnectionController` | `POST /api/v1/ssh/create_connection` | createConnection | 创建连接配置 |
| — | `POST /api/v1/ssh/update_connection` | updateConnection | 更新连接配置 |
| — | `POST /api/v1/ssh/delete_connection` | deleteConnection | 删除连接配置 |
| — | `GET  /api/v1/ssh/get_connection` | getConnection | 查询单个连接 |
| — | `GET  /api/v1/ssh/connection_list` | getConnectionList | 查询用户所有连接 |
| — | `POST /api/v1/ssh/connect` | connect | 建立 SSH 传输连接 |
| — | `POST /api/v1/ssh/disconnect` | disconnect | 断开 SSH 传输连接 |

### 3.2 Domain 层

| 类 | 职责 |
|---|---|
| `ISshConnectionDomainService` | 连接领域服务接口 |
| `SshConnectionService` | 领域服务实现，编排 Repository 和 SessionPort |
| `ISshConnectionRepository` | 连接仓储接口 |
| `ISshSessionPort` | SSH 传输层出站端口 |
| `SshConnectionEntity` | 连接实体，含 validate() 校验 |
| `SshConnectionConfigEntity` | 连接配置实体（超时、保活、压缩等） |
| `ConnectionStatusEnum` | 连接状态枚举：DISCONNECTED/CONNECTED/CONNECTING/FAILED |
| `AuthTypeEnum` | 认证类型枚举：PASSWORD/PRIVATE_KEY |

### 3.3 Infrastructure 层

| 类 | 职责 |
|---|---|
| `SshConnectionRepository` | 连接仓储实现，Entity ↔ PO 转换，密码/私钥加解密 |
| `SshSessionPort` | JSch Session 管理，connect/disconnect/isConnected/getSession |
| `ISshConnectionDAO` | 连接表 MyBatis DAO |
| `ISshConnectionConfigDAO` | 连接配置表 MyBatis DAO |
| `SshConnectionPO` | 连接表持久化对象 |
| `SshConnectionConfigPO` | 连接配置表持久化对象 |
| `PasswordEncryptor` | 密码加密/解密工具 |

### 3.4 数据库表

- `ssh_connection`：连接主表（connection_id 业务主键，密码/私钥加密存储）
- `ssh_connection_config`：连接高级配置表（超时、保活、压缩、主机密钥检查）

---

## 4. 设计细节

### 4.1 连接创建流程

```
createConnection(dto)
  ├── entity.validate()           // 校验必填字段
  ├── 生成 connectionId（UUID）
  ├── 设置默认值：status=DISCONNECTED, port=22, encrypted=1, userId="default"
  ├── repository.saveConnection(entity)
  └── repository.saveConnectionConfig(configEntity)  // 含 insertOrUpdate 策略
```

### 4.2 连接建立流程

```
connect(connectionId)
  ├── repository.queryConnectionById(connectionId)
  ├── sshSessionService.connect(connectionId, host, port, username, password, privateKey)
  │     ├── disconnect(connectionId)  // 先断旧连接
  │     ├── JSch.getSession(username, host, port)
  │     ├── 设置 StrictHostKeyChecking=no
  │     ├── 认证：私钥 → jsch.addIdentity；密码 → session.setPassword
  │     ├── session.connect()         // 30s 超时
  │     └── sessions.put(connectionId, session)
  ├── entity.setStatus(CONNECTED/FAILED)
  └── repository.updateConnection(entity)
```

### 4.3 密码/私钥加密策略

`SshConnectionRepository` 在写入数据库前自动加密，读取后自动解密：

- 写入时：若密码/私钥未被加密（`passwordEncryptor.isEncrypted()` 为 false），则加密后写入，`encrypted` 标记为 1
- 读取时：若 `encrypted=1` 且字段已加密，则解密返回
- 兼容脏数据：`encrypted=1` 但实际为明文的情况，解密失败时保留原值

### 4.4 连接状态同步

`getConnectionList` 接口在返回前会同步实际连接状态：

- 调用 `sshConnectionDomainService.isConnected(connectionId)` 获取真实传输层状态
- 若数据库状态与实际状态不一致，自动更新 entity.status
- 前端无需额外调用 connect/disconnect 即可看到最新状态

### 4.5 认证方式

| 认证类型 | 代码 | 实现 |
|---|---|---|
| 密码认证 | 1 | `session.setPassword(password)` |
| 私钥认证 | 2 | `jsch.addIdentity(connectionId, privateKey.getBytes(), null, null)` |

私钥认证时以 `connectionId` 作为 JSch identity 的 key，避免多连接冲突。

---

## 5. 接口契约

### 5.1 createConnection

```
POST /api/v1/ssh/create_connection
Request:  { "connectionName": "生产服务器", "host": "1.2.3.4", "port": 22, "username": "root", "authType": 1, "password": "xxx", ... }
Response: { "connectionId": "uuid", "connectionName": "...", ... }
```

- 密码/私钥至少提供一个
- `connectionId` 可不传，系统自动生成 UUID

### 5.2 updateConnection

```
POST /api/v1/ssh/update_connection
Request:  { "connectionId": "uuid", "connectionName": "新名称", ... }
Response: { "connectionId": "uuid", "connectionName": "新名称", ... }
```

- `connectionId` 必传，标识要更新的记录
- 密码/私钥留空则保留原值

### 5.3 connect / disconnect

```
POST /api/v1/ssh/connect?connectionId=uuid
Response: { "code": "0", "info": "连接成功" }

POST /api/v1/ssh/disconnect?connectionId=uuid
Response: { "code": "0", "info": "已断开连接" }
```

### 5.4 getConnectionList

```
GET /api/v1/ssh/connection_list?userId=default
Response: [{ "connectionId": "uuid", "connectionName": "...", "status": 1, ... }]
```

- 自动同步实际连接状态
- `userId` 可选，默认 "default"

---

## 6. 已知限制

- `SshSessionPort` 的 Session 缓存使用 `ConcurrentHashMap`，应用重启后所有传输连接丢失，需重新 connect
- 密码加密依赖 `PasswordEncryptor`，需确认加密密钥的注入方式和轮换策略
- `StrictHostKeyChecking` 固定设为 `no`，存在中间人攻击风险，生产环境应提供 `known_hosts` 支持
- 连接配置的 `startup_command` 字段已定义但当前未在连接建立后自动执行
- `getConnectionList` 对每个连接调用 `isConnected`，连接数量大时可能产生性能问题

---

## 7. 相关文档

- `doc/README.md` — 项目文档索引
- `doc/01-项目规范与架构约束.md` — DDD 分层、命名规范
- `doc/sql/ssh.sql` — SSH 连接表结构
- `doc/sql/ssh_config.sql` — SSH 连接配置表结构
- `doc/ssh/02-SSH-终端会话设计文档.md` — 终端交互实现（待补充）
