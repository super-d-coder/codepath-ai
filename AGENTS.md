# Agent 协作与文档维护规范

本文件约束在 `codepath-ai` 工作区内执行开发、修复和验收的 Agent。

## 1. 开始任务前

1. 阅读 `doc/README.md`；
2. 阅读 `doc/01-项目规范与架构约束.md`；
3. 阅读当前领域的计划和开发情况文件；
4. 检查当前工作树，避免覆盖其他未提交改动；
5. 未经明确要求，不修改 Agent / Armory 核心链路来实现 InfoFlow MVP。

## 2. 实时维护规则

### 2.1 何时更新文档

以下情况必须在同一个开发任务中同步更新文档：

| 变更类型 | 必须更新的位置 |
|---|---|
| 新领域、模块边界或依赖方向 | `doc/01-项目规范与架构约束.md`，必要时 `doc/README.md` |
| 领域功能开发进度、已完成范围、已知限制 | `doc/<领域>/01-<领域>开发情况.md` |
| API 路径、DTO、参数、响应语义变化 | 领域开发情况文件；必要时 README |
| 表、索引、去重策略、迁移脚本变化 | `doc/sql/` 和领域开发情况文件 |
| 配置项、环境变量、调度表达式变化 | 领域开发情况文件；不要记录真实密钥 |
| 已执行构建、测试、接口或数据库验收 | 领域开发情况文件的“验证记录” |
| 原计划范围或架构决策发生变化 | 对应计划文档追加“变更记录”，不要静默改写历史意图 |

### 2.2 更新时机

- 开始实现：若计划状态与事实不一致，先更新开发情况中的状态和待办；
- 完成一个可验收小闭环：立即更新“已实现内容”；
- 验证完成：立即在“验证记录”添加日期、命令 / 请求、结果、环境；
- 遇到阻塞：记录具体阻塞条件、影响范围和下一步，不要把未验证内容写成完成。

### 2.3 更新质量

- 使用“已实现”“已验证”“待验收”“已知限制”明确区分事实；
- 命令、接口和文件路径必须可复制或可定位；
- 不写 Token、密码、私钥、真实服务器地址；
- 不在多个文件重复维护同一份详细状态，`doc/README.md` 只维护索引；
- 不因为代码编译通过就声称外部 API 或数据库闭环已通过。

## 3. InfoFlow 专项约束

- 当前状态文件：`doc/infoflow/01-InfoFlow-MVP开发情况.md`；
- 原始计划：`doc/02-InfoFlow-MVP开发计划.md`；
- SQL：`doc/sql/infoflow.sql`；
- MVP 不接入 Agent、MCP、LLM、Memory、前端、Redis、Kafka 或 Elasticsearch；
- Domain 定义 Port / Repository 接口，Infrastructure 实现 HTTP / MyBatis，Trigger 只调用 Domain Service；
- 每次修改 InfoFlow 的接口、SQL、配置、采集策略、定时任务或验收状态，都必须更新状态文件。

## 4. 测试目录与 JUnit 规则

- 所有本项目测试统一放在 `codepath-ai-app/src/test/java/com/superd/test/`；
- 测试包按被测生产分层归位，不新建笼统的跨层测试目录：
  - Domain：`com.superd.test.domain.<领域>`；
  - Infrastructure：`com.superd.test.infrastructure.<领域>` 或其 `dao` 子包；
  - Trigger HTTP：`com.superd.test.trigger.http`；
  - API：`com.superd.test.api.<领域>`；
  - App：`com.superd.test.app`；
- 当前项目使用 JUnit 4，优先沿用 `@Test`、`@RunWith`、Mockito、`Assert` 和现有测试风格；
- `*Test` 表示离线单元测试，不能依赖外网、真实数据库或运行中的服务；
- `*ManualTest` 表示真实外部依赖验证，必须在类注释中说明网络/配置依赖，不能作为默认构建通过标准；
- InfoFlow 测试位置固定为：
  - `test/domain/infoflow/InfoFlowServiceTest.java`；
  - `test/infrastructure/infoflow/GitHubTrendingAdapterManualTest.java`；
  - `test/infrastructure/infoflow/HackerNewsAdapterManualTest.java`；
  - `test/trigger/http/InfoFlowControllerTest.java`；
- 修改生产接口、采集策略或 URL 编码时，必须同步更新对应分层测试和 `doc/infoflow/01-InfoFlow-MVP开发情况.md`。

## 5. 提交前最小检查

1. 确认修改文件符合模块依赖方向；
2. 执行与变更相匹配的构建或测试；
3. 更新领域开发情况与验证记录；
4. 检查工作树，确认没有意外生成物、敏感文件或无关改动；
5. 在最终说明中列出改动文件、验证命令、未验证项和已知限制。
