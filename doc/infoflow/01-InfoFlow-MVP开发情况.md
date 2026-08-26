# InfoFlow MVP 开发情况

> 状态：代码实现完成，已通过 Maven 编译；真实 MySQL、外部 API 和完整 HTTP 闭环仍需按环境执行最终验收。
>
> 更新时间：2026-08-25
>
> 对应计划：`doc/02-InfoFlow-MVP开发计划.md`
>
> README 渲染兼容协同方案：`doc/infoflow/03-GitHub-README渲染兼容协同方案.md`（后端第一版已实现）

## 1. 范围结论

InfoFlow MVP 当前只负责 GitHub 热门仓库与 Hacker News 资讯的采集、持久化和查询，不接入：

- Agent、MCP、LLM、Prompt；
- 会话记忆、长期记忆、向量数据库；
- 用户系统、用户画像、推荐；
- Redis、Kafka、Elasticsearch；
- 前端页面和 WebSocket。

## 2. 已实现内容

### 2.1 Domain

路径：`codepath-ai-domain/src/main/java/com/superd/domain/infoflow/`

- `HotRepoEntity`、`NewsItemEntity`；
- `SourceTypeEnum`、`CollectResultVO`；
- `IGitHubTrendingPort`、`IIndustryNewsPort`；
- `IHotRepoRepository`、`INewsItemRepository`；
- `IInfoFlowDomainService`、`InfoFlowService`。

`InfoFlowService` 负责采集编排、默认 `source` / `collectedAt`、失败结果、查询 `limit` 保护，以及 GitHub 成功采集后的过期快照清理。

### 2.2 Infrastructure

- GitHub Search API：按 `created:>=动态日期`、Star 降序查询；
- Hacker News Firebase API：读取 Top Stories，最多处理配置数量；
- Java `HttpClient` + Fastjson；
- GitHub 请求包含 JSON `Accept`、`User-Agent`，Token 可选；
- MyBatis DAO、PO、Repository 和 Mapper XML；
- 配置类：`InfoFlowProperties`。

### 2.3 持久化

SQL 文件：`doc/sql/infoflow.sql`

- `infoflow_hot_repo`：按 `(repo_full_name, collected_date)` 做每日快照去重，并保存 README 内容和状态；
- `infoflow_news_item`：按 `(source, external_id)` 做外部资讯去重；
- GitHub 写入使用 `ON DUPLICATE KEY UPDATE`；
- GitHub 采集同步获取 README Markdown，限制最大长度；根据默认分支和 README 实际路径规范化相对图片、文档链接及 HTML `src`/`href`；列表不查询 README 正文，详情返回规范化正文；
- GitHub 成功且有数据后清理超过 14 天的快照；失败或空结果不清理旧数据；
- Hacker News 写入使用 `INSERT IGNORE`；
- 查询使用显式列、resultMap 和排序字段。

### 2.4 API 与任务

- `GET /api/v1/infoflow/repos?language=Java&limit=20`（列表不返回 README 正文）；
- `GET /api/v1/infoflow/repos/{id}`（详情返回 README Markdown）；
- `GET /api/v1/infoflow/news?source=hacker_news&limit=20`；
- `POST /api/v1/infoflow/collect/github`；
- `POST /api/v1/infoflow/collect/news`；
- `POST /api/v1/infoflow/collect/all`；
- 查询接口 `limit` 默认 20，接口边界限制为 1–100；
- Controller 返回 API DTO，不暴露 Domain Entity、VO 或 PO；
- `collect/all` 按 GitHub → Hacker News 顺序执行，单源失败仍继续；
- `InfoFlowCollectJob` 复用领域服务；
- `Application` 已启用 `@EnableScheduling`。

## 3. 重要文件

```text
codepath-ai-api/src/main/java/com/superd/api/IInfoFlowService.java
codepath-ai-api/src/main/java/com/superd/api/dto/
codepath-ai-domain/src/main/java/com/superd/domain/infoflow/
codepath-ai-infrastructure/src/main/java/com/superd/infrastructure/config/InfoFlowProperties.java
codepath-ai-infrastructure/src/main/java/com/superd/infrastructure/adapter/port/
codepath-ai-infrastructure/src/main/java/com/superd/infrastructure/adapter/repository/
codepath-ai-app/src/main/resources/mybatis/mapper/infoflow_hot_repo_mapper.xml
codepath-ai-app/src/main/resources/mybatis/mapper/infoflow_news_item_mapper.xml
codepath-ai-trigger/src/main/java/com/superd/trigger/http/InfoFlowController.java
codepath-ai-trigger/src/main/java/com/superd/trigger/job/InfoFlowCollectJob.java
doc/sql/infoflow.sql
```

## 4. 配置与启动

当前激活配置为 `local`。在本地配置中确认：

```yaml
infoflow:
  github:
    api-base-url: https://api.github.com
    token: ${GITHUB_TOKEN:}
    lookback-days: 14
    per-page: 30
  hacker-news:
    api-base-url: https://hacker-news.firebaseio.com/v0
    top-story-limit: 30
    connect-timeout-seconds: 15
    request-timeout-seconds: 30
    max-retries: 2
    retry-delay-millis: 1000
  schedule:
    github-cron: "0 0 0 */6 * ?"
    news-cron: "0 0 0 */2 * ?"
```

不要将真实 GitHub Token 或数据库密码写入受版本控制的文档。先执行：

```text
doc/sql/infoflow.sql
```

再启动 `codepath-ai-app`。

## 5. 已执行验证

已执行：

```bash
mvn -DskipTests package
```

结果：7 个 Reactor 模块全部 `BUILD SUCCESS`。

已完成静态验收：

- Controller 实现 `IInfoFlowService`；
- 查询和采集接口返回 API DTO；
- Domain 不依赖 DAO、HTTP 客户端或 Infrastructure 实现；
- DAO / Mapper 方法名一致；
- 定时任务只调用领域服务；
- SQL 包含显式去重字段和唯一键。

## 6. 待环境验收

以下项目依赖本地 MySQL 和网络环境，尚未在所有环境完成真实闭环验证：

- 执行 SQL 后启动应用并确认 Mapper / DataSource 正常；
- GitHub API 正常限流条件下采集并写入仓库快照；
- Hacker News 采集并按 `external_id` 去重；
- 重复采集验证 GitHub 当日更新、Hacker News 不重复；
- 查询接口空库、正常数据和非法 limit；
- GitHub 403 / 网络失败 / Hacker News 缺失字段时的错误表现；
- 定时任务实际触发和日志输出。

完成上述项目后，Agent 必须在本文件的“验证记录”追加日期、命令、结果和环境说明。

## 7. 已知修复

- 2026-08-25：GitHub Search 查询参数曾仅替换空格与冒号，遗漏编码 `>`，导致 `URI.create()` 抛出 `Illegal character in query`；现已改为 `URLEncoder.encode(value, UTF_8)`，并将空格统一替换为 `%20`。
- 2026-08-26：联调中 Hacker News 出现 `HTTP connect timed out`。现已增加可配置的连接/请求超时和有限重试，默认额外重试 2 次、间隔 1 秒；最终失败仍返回 `success=false`，不影响其他来源。
- 2026-08-26：InfoFlow 采集新增 INFO 过程日志，输出来源、开始、获取数量和保存数量；重试输出 WARN，最终失败保留 ERROR 堆栈，不输出 Token 等敏感内容。
- 2026-08-26：GET 查询接口曾因编译产物不保留参数名，且 `@RequestParam` 未显式指定名称，导致 Spring 抛出 `Name for argument ... not specified`；现已为 `language`、`source`、`limit` 显式声明参数名，避免依赖 `-parameters`。
- 2026-08-26：GitHub 采集增加 README Markdown 获取、长度限制、状态记录、详情查询和成功后的 14 天过期快照清理；列表接口不返回 README 正文，采集失败或空结果不清理旧数据。

## 8. 分层测试与运行方式

所有测试统一位于 `codepath-ai-app/src/test/java/com/superd/test/`，并按被测生产层归位：

```text
com/superd/test/domain/infoflow/InfoFlowServiceTest.java
com/superd/test/infrastructure/infoflow/GitHubTrendingAdapterManualTest.java
com/superd/test/infrastructure/infoflow/HackerNewsAdapterManualTest.java
com/superd/test/trigger/http/InfoFlowControllerTest.java
```

- `InfoFlowServiceTest`：JUnit 4 + Mockito 离线验证领域编排、默认值、失败结果和 limit 保护；
- `InfoFlowControllerTest`：JUnit 4 + Mockito 离线验证 API DTO 映射、limit 边界和全量采集继续执行；
- 两个 `*ManualTest`：通过 Spring 容器访问真实 GitHub/Hacker News，不写数据库；依赖外网，GitHub Token 可选，必须显式指定测试类执行。

离线测试命令：

```powershell
mvn -pl codepath-ai-app -am '-Dskip.tests=false' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=InfoFlowServiceTest,InfoFlowControllerTest' test
```

真实外部 API 测试命令：

```powershell
mvn -pl codepath-ai-app -am '-Dskip.tests=false' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=GitHubTrendingAdapterManualTest,HackerNewsAdapterManualTest' test
```

当前阶段暂不新增 Mock HTTP、MySQL 集成测试或 REST 到数据库全链路测试。普通 `mvn -DskipTests package` 只代表编译/打包成功，不代表真实外部 API 或数据库闭环已通过。

## 9. 验证记录

| 日期 | 验证项 | 命令 / 请求 | 结果 | 环境 |
|---|---|---|---|---|
| 2026-08-25 | Maven 全工程编译 | `mvn -DskipTests package` | 7 模块 BUILD SUCCESS | 本地开发环境 |
| 2026-08-25 | GitHub 查询 URL 编码修复构建 | `mvn -pl codepath-ai-infrastructure,codepath-ai-app -am -DskipTests package` | BUILD SUCCESS | 本地开发环境 |
| 2026-08-25 | InfoFlow Domain / Controller 离线测试 | `mvn -pl codepath-ai-app -am '-Dskip.tests=false' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=InfoFlowServiceTest,InfoFlowControllerTest' test` | 9 tests passed | 本地开发环境 |
| 2026-08-25 | GitHub / Hacker News 真实 API 测试 | `mvn -pl codepath-ai-app -am '-Dskip.tests=false' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=GitHubTrendingAdapterManualTest,HackerNewsAdapterManualTest' test` | 2 tests passed；GitHub 获取 30 条，Hacker News 获取 29 条 | 本地网络环境 |
| 待补充 | GitHub 手动采集 REST API | `POST /api/v1/infoflow/collect/github` | URL 编码问题已修复；需重启最新应用后复验数据库写入 | 本地开发环境 |
| 2026-08-26 | InfoFlow 过程日志与 Hacker News 重试 | 配置连接/请求超时、有限重试和开始/获取/成功日志 | 已实现；离线 9 tests passed；真实 Hacker News 网络复验待执行 | 本地开发环境 |
| 2026-08-26 | GitHub README 路径规范化 | `GitHubReadmeNormalizerTest,InfoFlowServiceTest,InfoFlowControllerTest` | 15 tests passed；真实 GitHub 样本复验待执行 | 本地开发环境 |
| 待补充 | MySQL 与 REST 数据闭环 | 见第 6 节 | 待验收 | 待补充 |
