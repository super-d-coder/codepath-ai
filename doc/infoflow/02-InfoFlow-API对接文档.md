# InfoFlow API 对接文档

> 面向对象：前端工程师、接口联调人员。
>
> 文档性质：当前后端代码的接口契约。本文只描述数据与调用方式，不约束前端页面、组件、交互或样式设计。
>
> 当前验证状态：GitHub/Hacker News 外部采集及 Domain/Controller 单元测试已通过；MySQL 持久化与 REST 数据闭环尚待实测，详见 `doc/infoflow/01-InfoFlow-MVP开发情况.md`。
>
> GitHub README 相对资源路径、GFM、HTML 安全渲染和 Tauri 外链协同方案见 `doc/infoflow/03-GitHub-README渲染兼容协同方案.md`；后端路径规范化第一版已实现，GFM/HTML Sanitizer/Tauri 外链仍由前端负责。

## 1. 基础信息

| 项目 | 值 |
|---|---|
| 本地默认服务地址 | `http://127.0.0.1:8091` |
| API 前缀 | `/api/v1/infoflow` |
| 响应格式 | `application/json` |
| 字符编码 | UTF-8 |
| 当前鉴权 | 无 |

前端工程可通过环境变量覆盖后端地址：

```text
VITE_API_BASE_URL=http://127.0.0.1:8091
```

注意：

- GitHub Token 只在后端通过 `GITHUB_TOKEN` 配置，前端不传递、不保存 Token；
- Hacker News 官方 API 不需要 Key；
- 当前手动采集接口没有登录鉴权，部署到公网前应由网关或后续权限模块限制访问；
- 浏览器直连后端时后端 Controller 当前允许跨域；Vite 开发环境也可以代理 `/api` 到后端。

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
  "data": []
}
```

常用顶层状态码：

| code | 含义 |
|---|---|
| `0000` | 后端已正常处理请求 |
| `0001` | 未知失败 |
| `0002` | 非法参数 |

### 2.1 采集接口的双层成功语义

采集接口需要依次判断：

1. HTTP 请求是否成功；
2. 响应是否为合法 JSON；
3. 顶层 `code` 是否为 `"0000"`；
4. `data.success` 是否为 `true`。

原因是领域服务会把 GitHub/Hacker News 的外部请求失败转换成采集结果。因此可能出现：

```json
{
  "code": "0000",
  "info": "成功",
  "data": {
    "source": "github",
    "fetchedCount": 0,
    "savedCount": 0,
    "success": false,
    "message": "GitHub API request failed",
    "collectedAt": "2026-08-25T22:10:00"
  }
}
```

此时表示 InfoFlow 接口被正常调用，但本次外部数据采集失败。前端不能只检查 HTTP 200 或顶层 `code`。

## 3. TypeScript 数据类型

### 3.1 GitHub 热门仓库

```ts
export interface HotRepoResponseDTO {
  id: number;
  fullName: string;
  name: string;
  description: string | null;
  htmlUrl: string;
  stars: number;
  forks: number;
  language: string | null;
  /** 后端当前以英文逗号分隔的字符串返回，可能为 null。 */
  topics: string | null;
  source: "github" | string;
  /** GitHub 仓库创建时间。 */
  publishedAt: string | null;
  collectedAt: string;
}
```

### 3.2 Hacker News 资讯

```ts
export interface NewsItemResponseDTO {
  id: number;
  externalId: string;
  title: string;
  url: string;
  author: string | null;
  summary: string | null;
  source: "hacker_news" | string;
  publishedAt: string | null;
  collectedAt: string;
}
```

### 3.3 采集结果

```ts
export interface CollectResponseDTO {
  source: "github" | "hacker_news" | string;
  fetchedCount: number;
  savedCount: number;
  success: boolean;
  message: string;
  collectedAt: string;
}
```

### 3.4 时间字段

后端字段类型为 `LocalDateTime`，JSON 通常形如：

```text
2026-08-25T22:10:00
```

该值不包含时区偏移。前端应按本地日期时间处理，不应假定它是带 `Z` 的 UTC 时间。

## 4. 查询 GitHub 热门仓库

### 4.1 请求

```http
GET /api/v1/infoflow/repos
```

可选参数：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---:|---:|---|
| `language` | string | 否 | 无 | 按主要编程语言精确筛选，如 `Java` |
| `limit` | integer | 否 | 20 | Controller 会保护到 `1–100` 范围 |

示例：

```http
GET /api/v1/infoflow/repos?language=Java&limit=20
```

### 4.2 响应

```json
{
  "code": "0000",
  "info": "成功",
  "data": [
    {
      "id": 1,
      "fullName": "owner/repository",
      "name": "repository",
      "description": "Repository description",
      "htmlUrl": "https://github.com/owner/repository",
      "stars": 1000,
      "forks": 100,
      "language": "Java",
      "topics": "spring,ai,java",
      "source": "github",
      "publishedAt": "2026-08-20T10:00:00",
      "collectedAt": "2026-08-25T22:00:00"
    }
  ]
}
```

说明：

- 返回顺序：先按 `collectedAt` 降序，再按 Stars 降序；
- 空库或筛选无结果时，成功返回空数组 `[]`；
- `description`、`language`、`topics`、`publishedAt` 可能为 `null`；
- `topics` 当前不是数组，而是逗号分隔字符串；前端如需标签数组可自行按 `,` 分割并过滤空项。

## 5. 查询 GitHub 仓库详情

### 5.1 请求

```http
GET /api/v1/infoflow/repos/{id}
```

路径参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `id` | long | 是 | InfoFlow 仓库记录 ID |

### 5.2 响应

详情响应包含列表字段，并额外返回 README 字段：

```json
{
  "code": "0000",
  "info": "成功",
  "data": {
    "id": 1,
    "fullName": "owner/repository",
    "name": "repository",
    "description": "Repository description",
    "htmlUrl": "https://github.com/owner/repository",
    "stars": 1000,
    "forks": 100,
    "language": "Java",
    "topics": "spring,ai,java",
    "source": "github",
    "publishedAt": "2026-08-20T10:00:00",
    "collectedAt": "2026-08-25T22:00:00",
    "readmeContent": "# Repository\\n\\nREADME Markdown 内容",
    "readmeUrl": "https://github.com/owner/repository#readme",
    "readmeStatus": "available",
    "readmeFetchedAt": "2026-08-25T22:00:00"
  }
}
```

`readmeStatus` 当前约定：

- `available`：README 已成功获取；
- `unavailable`：README 获取失败；
- `null`：未尝试获取或数据库中没有 README 信息。

README 内容由后端返回原始 Markdown，前端自行选择 Markdown 渲染器。`htmlUrl` 和 `readmeUrl` 可用于提供 GitHub 外链。

记录不存在时返回失败响应，`info` 为“仓库不存在”。

## 6. 查询 Hacker News 资讯

### 5.1 请求

```http
GET /api/v1/infoflow/news
```

可选参数：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---:|---:|---|
| `source` | string | 否 | 无 | 当前数据源通常为 `hacker_news` |
| `limit` | integer | 否 | 20 | Controller 会保护到 `1–100` 范围 |

示例：

```http
GET /api/v1/infoflow/news?source=hacker_news&limit=20
```

### 5.2 响应

```json
{
  "code": "0000",
  "info": "成功",
  "data": [
    {
      "id": 1,
      "externalId": "12345678",
      "title": "Example story",
      "url": "https://example.com/story",
      "author": "example-user",
      "summary": null,
      "source": "hacker_news",
      "publishedAt": "2026-08-25T20:00:00",
      "collectedAt": "2026-08-25T22:00:00"
    }
  ]
}
```

说明：

- 返回顺序：先按 `publishedAt` 降序，再按 `collectedAt` 降序；
- 空库或筛选无结果时，成功返回空数组 `[]`；
- `author`、`summary`、`publishedAt` 可能为 `null`；
- Ask HN 等没有外部 URL 的记录，后端会将 `url` 补成 Hacker News item 页面。

## 7. 手动采集 GitHub

### 6.1 请求

```http
POST /api/v1/infoflow/collect/github
```

- 不需要请求体；
- 不需要前端传 GitHub Token；
- 请求头可不设置 `Content-Type`，也可以使用 `application/json`；
- 当前操作会访问 GitHub Search API，并尝试写入 MySQL。

### 6.2 成功响应

```json
{
  "code": "0000",
  "info": "成功",
  "data": {
    "source": "github",
    "fetchedCount": 30,
    "savedCount": 30,
    "success": true,
    "message": "GitHub 热门仓库采集成功",
    "collectedAt": "2026-08-25T22:00:00"
  }
}
```

## 8. 手动采集 Hacker News

### 7.1 请求

```http
POST /api/v1/infoflow/collect/news
```

- 不需要请求体；
- 不需要任何 API Key；
- 当前操作会访问 Hacker News Firebase API，并尝试写入 MySQL。

### 7.2 成功响应

```json
{
  "code": "0000",
  "info": "成功",
  "data": {
    "source": "hacker_news",
    "fetchedCount": 29,
    "savedCount": 29,
    "success": true,
    "message": "行业资讯采集成功",
    "collectedAt": "2026-08-25T22:00:00"
  }
}
```

## 9. 手动采集全部来源

### 8.1 请求

```http
POST /api/v1/infoflow/collect/all
```

- 不需要请求体；
- 按 GitHub → Hacker News 的顺序执行；
- 一个来源失败不会阻止另一个来源执行；
- 返回值固定为来源结果数组，前端应逐项检查 `success`。

### 8.2 响应

```json
{
  "code": "0000",
  "info": "成功",
  "data": [
    {
      "source": "github",
      "fetchedCount": 30,
      "savedCount": 30,
      "success": true,
      "message": "GitHub 热门仓库采集成功",
      "collectedAt": "2026-08-25T22:00:00"
    },
    {
      "source": "hacker_news",
      "fetchedCount": 29,
      "savedCount": 29,
      "success": true,
      "message": "行业资讯采集成功",
      "collectedAt": "2026-08-25T22:00:10"
    }
  ]
}
```

## 10. 前端调用参考

以下代码只展示接口调用，不规定前端状态管理或页面设计：

```ts
const API_BASE_URL = (
  import.meta.env.VITE_API_BASE_URL ?? "http://127.0.0.1:8091"
).replace(/\/$/, "");

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}/api/v1/infoflow${path}`, init);
  const payload = (await response.json()) as ApiResponse<T>;

  if (!response.ok || String(payload.code) !== "0000") {
    throw new Error(payload.info || `请求失败（HTTP ${response.status}）`);
  }

  return payload.data as T;
}

export const infoflowApi = {
  queryRepos(language?: string, limit = 20) {
    const query = new URLSearchParams({ limit: String(limit) });
    if (language) query.set("language", language);
    return request<HotRepoResponseDTO[]>(`/repos?${query}`);
  },

  queryNews(source?: string, limit = 20) {
    const query = new URLSearchParams({ limit: String(limit) });
    if (source) query.set("source", source);
    return request<NewsItemResponseDTO[]>(`/news?${query}`);
  },

  collectGithub() {
    return request<CollectResponseDTO>("/collect/github", { method: "POST" });
  },

  collectNews() {
    return request<CollectResponseDTO>("/collect/news", { method: "POST" });
  },

  collectAll() {
    return request<CollectResponseDTO[]>("/collect/all", { method: "POST" });
  },
};
```

采集后建议由前端工程师根据最终交互方案决定是否重新调用对应 GET 接口。后端不会通过 WebSocket 主动推送刷新结果。

## 11. 后端过程日志

后端采集接口会在控制台输出可检索的简短过程日志，便于确认请求是否真正进入后端：

```text
InfoFlow github 采集开始
InfoFlow github 数据获取完成，获取 30 条
InfoFlow github 采集成功，获取 30 条，保存 30 条
```

Hacker News 使用 `hacker_news` 作为来源标识。外部请求临时失败并重试时会输出当前重试次数；最终失败会输出带异常堆栈的 ERROR 日志。日志不会输出 Token、密码或完整授权头。

查询接口不会逐条打印数据，避免日志噪声和数据泄漏。

## 12. 错误处理建议

| 场景 | 前端可观察结果 | 建议 |
|---|---|---|
| 后端未启动或地址错误 | `fetch` 网络异常 | 提示检查后端和 `VITE_API_BASE_URL` |
| 后端返回非 JSON | JSON 解析异常 | 提示服务响应无效并记录 HTTP 状态 |
| 顶层业务失败 | `code != "0000"` | 显示 `info` |
| GitHub 限流或网络失败 | 顶层可能为 `0000`，但 `data.success=false` | 显示采集 `message`，不要将其视为采集成功 |
| Hacker News 网络失败 | 后端按配置有限重试；最终仍失败时 `data.success=false` | 显示来源和 message |
| 查询无数据 | `data=[]` | 按空状态处理，不视为异常 |
| `collect/all` 部分失败 | 数组中部分 `success=false` | 分来源展示结果，不丢弃成功来源 |
| MySQL 写入失败 | 采集结果 `success=false`，message 描述失败 | 提示后端持久化失败，勿只显示“已抓取” |

## 13. 数据与去重语义

- GitHub 热门定义：最近 N 天内创建、按总 Stars 降序的仓库，不等同于 GitHub 网站 Trending 页面；
- GitHub 表按 `(repo_full_name, collected_date)` 做每日快照，同一天重复采集执行更新；成功且返回非空数据后清理 14 天以前的快照，失败或空结果不清理；
- Hacker News 按 `(source, external_id)` 去重，重复资讯不重复插入；
- `savedCount` 是数据库写入语句受影响数量，不等价于页面新增卡片数量；重复采集时其含义受 MySQL upsert/ignore 影响；
- 前端展示应以随后查询接口返回的数据为准，不应仅根据 `savedCount` 构造列表。

## 14. 当前后端验证边界

已验证：

- GitHub 真实 API 获取和字段转换；
- Hacker News 真实 API 获取和字段转换；
- InfoFlow Domain 采集编排；
- Controller DTO 映射与 limit 保护；
- Maven 编译与分层 JUnit 测试。

待验证：

- MySQL 建表脚本在目标数据库执行；
- MyBatis 实际写入、查询与时间映射；
- 重复采集的数据库去重结果；
- REST → Domain → MyBatis → MySQL 完整闭环。

前端工程师可以依据本文先完成接口类型和调用封装，但在后端数据库闭环完成前，不应把示例数据视为目标环境的实测响应。

## 15. 编译与参数绑定说明

查询接口已显式声明参数名：`language`、`source`、`limit`。前端按本文参数名传递即可，不依赖后端是否使用 Java 编译器 `-parameters` 选项。

## 16. 后端接口变更约定

若后续变更以下内容，后端开发人员必须同步更新本文：

- API 路径或 HTTP 方法；
- 查询参数、默认值或边界；
- DTO 字段或可空性；
- 顶层状态码或采集成功语义；
- 时间格式；
- 去重策略；
- 鉴权、CORS 或部署地址规则。
