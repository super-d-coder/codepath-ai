# GitHub README 渲染兼容协同方案

> 状态：后端第一版已实现；前端 GFM 渲染、HTML Sanitizer、Tauri 外链处理仍由前端工程师按本文协同完成。
>
> 目标：在不破坏 README 原始 Markdown 结构的前提下，使 GitHub README 能在 Web/Tauri 前端中稳定渲染，并避免外部链接替换桌面应用页面。
>
> 职责边界：后端负责 GitHub 仓库上下文相关的 URL 规范化和最低限度安全处理；前端负责 GFM Markdown 渲染、HTML 安全渲染、图片失败状态和桌面端外链打开。

## 1. 问题结论

GitHub README API 返回的 `content` 经 Base64 解码后，本质上仍是 Markdown 文件。正常 Markdown 内容不应由后端重新编写或交给 Agent 清洗。

当前显示问题主要来自以下差异：

1. GitHub 在仓库和默认分支上下文中解析相对图片路径，独立前端没有该上下文；
2. README 可能包含相对文档链接，直接点击会在当前 WebView 内导航；
3. GitHub 使用 GFM，普通 Markdown 渲染器未必默认支持表格、任务列表、删除线等语法；
4. README 经常包含少量 HTML 标签，前端禁用 HTML 时会显示为标签文本，直接启用又存在安全风险；
5. Tauri WebView 内直接打开外部页面会替换当前应用内容，可能无法正常交互或返回。

因此采用：

```text
GitHub README Markdown 原文
        ↓
后端补齐仓库上下文相关 URL
        ↓
前端使用 GFM + 安全 HTML 渲染
        ↓
外部链接交给系统浏览器
```

## 2. 设计原则

### 2.1 不使用 Agent 处理基础兼容问题

以下问题由确定性程序规则解决：

- Markdown 图片相对路径；
- HTML `<img src>` 相对路径；
- Markdown 相对文档链接；
- HTML 链接路径；
- 危险协议、危险标签和事件属性；
- Tauri 外部链接跳转。

Agent/LLM 后续只适合：

- 提炼项目用途；
- 生成简洁摘要；
- 识别技术栈；
- 生成标签和推荐理由。

Agent 产物不得覆盖 README 内容。

### 2.2 保留 Markdown，不做大规模转换

后端不得：

- 将完整 Markdown 转成 HTML 后入库；
- 删除正常标题、列表、表格、代码块、引用；
- 为了兼容单一渲染器重写 README 语义；
- 让 Agent 自由判断并删除“无法显示”的内容。

### 2.3 前后端职责清晰

| 能力 | 后端 | 前端 |
|---|---:|---:|
| GitHub API 获取 README | 是 | 否 |
| Base64 解码 | 是 | 否 |
| 获取默认分支/README 路径上下文 | 是 | 否 |
| Markdown 相对图片路径转绝对路径 | 是 | 否 |
| HTML `<img src>` 相对路径转绝对路径 | 是 | 否 |
| Markdown 相对文档链接转 GitHub URL | 是 | 否 |
| 保留正常 Markdown/GFM 语法 | 是 | 是 |
| GFM 渲染 | 否 | 是 |
| HTML Sanitizer | 可做最低限度协议检查 | 是，最终渲染前必须执行 |
| 图片加载失败占位 | 否 | 是 |
| 外部链接交给系统浏览器 | 否 | 是 |
| 应用内详情页返回 | 否 | 是 |
| 前端页面和样式 | 否 | 是 |

## 3. 后端处理方案

### 3.1 输入信息

README 规范化至少需要：

```text
owner/repo
默认分支，例如 main
README 文件路径，例如 README.md 或 docs/README.md
README Markdown 原文
```

后端不能假定默认分支一定是 `main`。应从 GitHub 仓库响应或 README API 响应中获得准确上下文。

### 3.2 Markdown 图片路径

原始内容：

```markdown
![Logo](./docs/logo.png)
```

转换后：

```markdown
![Logo](https://raw.githubusercontent.com/owner/repo/main/docs/logo.png)
```

处理范围：

```text
./docs/logo.png
docs/logo.png
../assets/logo.png
/assets/logo.png
```

路径解析必须以 README 所在目录为基础，而不是永远以仓库根目录为基础。

以下地址不转换：

```text
https://...
http://...
data:image/...
mailto:...
#section-anchor
```

`data:image` 是否允许最终展示，由前端 Sanitizer 策略决定。

### 3.3 HTML 图片路径

原始内容：

```html
<img src="./docs/logo.png" alt="Logo">
```

只规范化 `src` 地址，不改写标签布局：

```html
<img src="https://raw.githubusercontent.com/owner/repo/main/docs/logo.png" alt="Logo">
```

不执行 HTML 中的脚本或事件属性。

### 3.4 Markdown 相对链接

原始内容：

```markdown
[安装说明](./docs/install.md)
```

转换为 GitHub 页面链接：

```markdown
[安装说明](https://github.com/owner/repo/blob/main/docs/install.md)
```

目录链接可转换为：

```text
https://github.com/owner/repo/tree/main/path
```

第一版无法可靠区分文件和目录时，可以统一使用 GitHub `blob` URL；GitHub 页面负责后续呈现。

锚点链接保持不变：

```markdown
[安装](#installation)
```

### 3.5 HTML 链接

原始内容：

```html
<a href="./docs/install.md">安装说明</a>
```

只规范化 `href`，不将 HTML 链接转换成 Markdown。

### 3.6 URL 安全约束

后端必须拒绝或移除危险协议：

```text
javascript:
vbscript:
file:
```

对以下内容不主动执行：

```html
<script>
<iframe>
<object>
<embed>
```

但最终 HTML 白名单和 Sanitizer 仍由前端渲染层负责，因为危险内容最终在前端执行环境中产生影响。

### 3.7 内容长度

继续使用配置：

```yaml
infoflow:
  github:
    readme-max-chars: 200000
```

规范化应在明确的长度策略下执行。若内容被截断，需要在状态或后续字段中可识别，不能静默让前端误以为 README 完整。

## 4. 后端数据建议

### 4.1 当前最小兼容方案

当前详情接口继续返回：

```text
readmeContent
readmeUrl
readmeStatus
readmeFetchedAt
```

`readmeContent` 的语义调整为：

> 已完成相对资源路径规范化、仍保持 Markdown/GFM 结构的展示内容。

### 4.2 推荐演进方案

为便于重新处理和后续 Agent 分析，建议最终拆分：

```text
readmeRawContent       GitHub 原始 Markdown
readmeRenderContent    URL 规范化后的 Markdown
readmeCleanerVersion   规范化规则版本
readmeCleanedAt        规范化时间
```

第一阶段可以先不把 `readmeRawContent` 暴露给普通前端接口，但建议数据库保留原文。

## 5. 后端接口契约

### 5.1 列表接口

```http
GET /api/v1/infoflow/repos?language=Java&limit=20
```

列表只返回简洁信息，不返回 README 正文：

```json
{
  "id": 1,
  "fullName": "owner/repo",
  "name": "repo",
  "description": "项目简介",
  "htmlUrl": "https://github.com/owner/repo",
  "stars": 1000,
  "forks": 100,
  "language": "Java",
  "topics": "spring,ai",
  "source": "github",
  "publishedAt": "2026-08-20T10:00:00",
  "collectedAt": "2026-08-26T20:00:00"
}
```

### 5.2 详情接口

```http
GET /api/v1/infoflow/repos/{id}
```

返回列表字段和 README：

```json
{
  "code": "0000",
  "info": "成功",
  "data": {
    "id": 1,
    "fullName": "owner/repo",
    "name": "repo",
    "description": "项目简介",
    "htmlUrl": "https://github.com/owner/repo",
    "stars": 1000,
    "forks": 100,
    "language": "Java",
    "topics": "spring,ai",
    "source": "github",
    "publishedAt": "2026-08-20T10:00:00",
    "collectedAt": "2026-08-26T20:00:00",
    "readmeContent": "# README\n\n已规范化资源路径的 Markdown",
    "readmeUrl": "https://github.com/owner/repo/blob/main/README.md",
    "readmeStatus": "available",
    "readmeFetchedAt": "2026-08-26T20:00:00"
  }
}
```

README 状态继续使用：

```text
available
unavailable
null
```

若新增 `truncated` 或 `normalize_failed` 状态，需要先更新 API 文档并通知前端工程师。

## 6. 前端渲染要求

### 6.1 使用 GFM

前端 Markdown 渲染器必须启用 GitHub Flavored Markdown，至少支持：

- 表格；
- 删除线；
- Task List；
- 自动链接；
- 代码块；
- 常见 GitHub README 排版。

### 6.2 HTML 渲染与 Sanitizer

如果产品决定支持 README 内嵌 HTML：

```text
Markdown 解析
→ HTML 白名单/Sanitizer
→ 渲染
```

不能仅开启“允许原始 HTML”而不做清理。

建议禁止：

```text
script
iframe
object
embed
form
事件属性 on*
javascript: URL
```

可以按前端渲染器能力讨论是否允许：

```text
br
p
img
a
details
summary
table
thead
tbody
tr
th
td
sub
sup
```

### 6.3 外部链接

README 内所有外部链接和 GitHub 页面链接，前端必须拦截：

```text
阻止 WebView 默认导航
→ 调用 Tauri opener
→ 使用系统默认浏览器打开
```

不能让外部网页替换当前桌面应用内容。

锚点链接：

```text
#installation
```

可以在当前 README 详情页内滚动定位，不需要打开系统浏览器。

### 6.4 图片失败状态

前端应处理图片加载失败：

- 显示简洁占位；
- 保留 `alt` 文本；
- 不显示整段 Markdown 或异常堆栈；
- 不因单张图片失败阻断整个 README 渲染。

### 6.5 详情页返回

列表页和详情页之间由前端路由或本地状态管理。外部链接不得改变前端当前路由，因此用户从系统浏览器返回时，应用仍停留在原详情页。

## 7. 前后端协同开发顺序

### 7.1 后端任务

1. 获取仓库默认分支和 README 实际路径；
2. 编写 Markdown URL 规范化组件；
3. 处理 Markdown 图片、Markdown 链接、HTML `src` 和 `href`；
4. 增加危险协议保护；
5. 保持列表接口不返回 README；
6. 详情接口返回规范化后的 README；
7. 增加清洗规则离线测试；
8. 使用真实 GitHub README 做手工验证；
9. 更新 `02-InfoFlow-API对接文档.md`。

### 7.2 前端任务

1. 选定支持 GFM 的 Markdown 渲染器；
2. 确认原始 HTML 是否启用及白名单；
3. 接入 HTML Sanitizer；
4. 拦截 `<a>` 点击；
5. Tauri 环境使用系统浏览器打开外链；
6. 锚点在详情页内部定位；
7. 图片失败显示占位；
8. 验证列表→详情→返回流程。

### 7.3 联调顺序

```text
后端用真实 README 验证 URL 规范化
→ 前端用固定详情响应验证 GFM 渲染
→ 前后端连接真实详情接口
→ 验证图片、HTML、外链、锚点和返回
```

## 8. 测试样本

联调至少覆盖以下 README：

1. 只有标准 Markdown；
2. 包含相对图片；
3. README 位于子目录；
4. 包含相对 `.md` 链接；
5. 包含 `<img>`、`<details>`、表格；
6. 包含徽章和 SVG；
7. 包含锚点链接；
8. 包含外部 HTTP/HTTPS 链接；
9. 没有 README；
10. 超过长度限制的 README。

## 9. 验收标准

### 后端

- README 仍保持 Markdown/GFM 内容；
- 相对图片路径转换正确；
- 相对文档链接转换正确；
- README 子目录路径解析正确；
- 外部 URL 和锚点不被错误改写；
- 危险协议不进入展示内容；
- 列表接口不返回 README 正文；
- 详情接口返回规范化 Markdown；
- 单仓库规范化失败不影响其他仓库元数据采集。

### 前端

- 标题、列表、代码块、表格、Task List 正常；
- 支持范围内的安全 HTML 正常；
- 图片可以显示，失败时有占位；
- 外部链接在系统浏览器打开；
- 外部页面不替换 Tauri 应用；
- 锚点在当前详情页定位；
- 可以从详情返回列表。

## 10. 暂不实施

本阶段不做：

- Agent/LLM README 清洗；
- GitHub 源码抓取；
- README 自动摘要；
- 项目价值评分；
- Hacker News 正文处理；
- 前端样式设计；
- 将 Markdown 全量转换并保存为 HTML。

## 11. 后端第一版实现记录

已实现：

- GitHub Search 响应读取 `default_branch`；
- README API 使用实际 `path` 作为相对路径基准；
- Markdown 图片相对路径转 `raw.githubusercontent.com`；
- Markdown 文档相对链接转 GitHub `blob` 页面；
- HTML `src` / `href` 相对路径规范化；
- 外部 HTTP/HTTPS、`mailto:`、锚点保持不变；
- `javascript:`、`vbscript:`、`file:` 转为安全占位 `#`；
- 列表接口不查询 README 正文；详情接口返回规范化后的 `readmeContent`；
- 新增 6 个离线规范化测试，连同既有 Domain/Controller 测试共 15 个通过。

当前限制：

- 第一版使用确定性正则处理常见 Markdown 链接，不是完整 Markdown AST 解析器；复杂嵌套括号 URL、Reference-style definitions、HTML `srcset` 后续可按真实样本扩展；
- 未在本轮调用真实 GitHub API，真实仓库样本仍需联调复验；
- HTML 最终白名单和 Sanitizer 仍必须在前端渲染层执行；
- Tauri 外链拦截由前端负责。

## 12. 变更控制

本文经用户与前端工程师确认后作为本阶段实现基线。若开发中需要：

- 改变详情响应字段；
- 新增 README 状态；
- 暴露原始 README；
- 改变 HTML 白名单；
- 改变外链打开策略；

必须先更新本文并确认，再修改代码。
