# codepath-ai

> 一个面向开发者的 Java Agent 平台，目标是把 SSH 云服务器控制、AI IDE 能力、可配置 Agent 工作流和行业信息获取能力统一到一个可扩展的系统中。

## 项目定位

`codepath-ai` 不是单一的聊天机器人，而是一个面向开发者工作流的 Agent 基础平台。

当前和规划中的能力包括：

- SSH 连接和远程服务器操作；
- 云服务器控制能力；
- 面向代码仓库的 AI IDE 能力；
- 基于 Google ADK / Spring AI 的 Agent 装配；
- MCP Tool 与 Skills 扩展；
- Sequential / Parallel / Loop 工作流编排；
- 计算机行业资讯和 GitHub 热门项目采集；
- 后续可扩展记忆、用户画像、自主规划和 Agent 反思能力。

## 技术栈

### 后端

- Java 17
- Spring Boot 3.4.3
- Maven 多模块
- MyBatis
- MySQL
- JSch
- Google ADK 1.5.0
- Spring AI
- LangChain4j
- MCP
- Guava

### 前端

前端位于独立项目：

```text
D:\Projects\JAVA\codepath-ai-client
```

使用：

- React 19
- TypeScript
- Vite
- Tauri 2
- Zustand
- Lucide React

## 项目结构

```text
codepath-ai/
├── codepath-ai-api             # 对外接口、DTO、统一响应体
├── codepath-ai-app             # Spring Boot 启动模块和配置资源
├── codepath-ai-domain          # DDD 领域层和 Agent 核心逻辑
├── codepath-ai-infrastructure  # DAO、MyBatis、外部系统适配
├── codepath-ai-trigger         # Controller、定时任务、监听器
├── codepath-ai-types           # 公共类型、枚举、异常
├── doc/                        # SQL 和项目设计文档
└── pom.xml
```

## 架构设计

项目遵循 DDD 与端口-适配器思想。

```text
Trigger / API
      ↓
Domain Service
      ↓
Domain Port / Repository Interface
      ↓
Infrastructure Adapter / Repository
      ↓
External API / MyBatis / Database
```

领域层定义业务规则和外部依赖接口，基础设施层实现这些接口，触发器层负责 HTTP 和任务调度。

## Agent 装配机制

项目当前使用 YAML 驱动的 Agent 装配流程：

```text
YAML 配置
  ↓
AiApiNode
  ↓
ChatModelNode
  ↓
AgentNode
  ↓
AgentWorkflowNode
  ↓
Sequential / Parallel / Loop
  ↓
RunnerNode
  ↓
InMemoryRunner
```

YAML 可以定义：

- AI API 的 Base URL、API Key 和模型；
- MCP Tools；
- Skills；
- Agent 名称、描述、Instruction、Output Key；
- Agent Workflow 类型和子 Agent；
- Plugin。

已有工作流类型：

```text
sequential  # 串行执行
parallel    # 并行执行
loop        # 循环执行
```

当前架构更准确的描述是：

> Workflow-driven Agent Assembly System（工作流驱动的 Agent 装配系统）

其中工作流负责编排，工作流节点内的 LLM Agent 负责调用模型和工具。记忆、自主规划和反思能力属于后续演进方向，不应在 MVP 阶段假定已经具备。

## InfoFlow MVP

InfoFlow 是一个新的 `infoflow` 领域上下文，第一阶段只负责数据基础设施，不接入 Agent。

### MVP 功能

- 获取 GitHub 最近创建且 Star 较高的热门项目；
- 获取 Hacker News 技术资讯；
- 存储到 MySQL；
- 提供 REST 查询接口；
- 提供手动采集接口；
- 通过定时任务自动刷新。

### 暂不包含

- LLM 分析；
- Agent；
- MCP Tool；
- 记忆系统；
- 用户画像；
- 推荐系统；
- 前端信息流页面。

### MVP 数据流

```text
GitHub Search API ──┐
                    ├─ Infrastructure Adapter
Hacker News API ────┘          ↓
                       InfoFlow Domain Service
                                ↓
                         MyBatis Repository
                                ↓
                              MySQL
                                ↓
                         REST / Scheduled Job
```

### 推荐数据表

```text
infoflow_hot_repo
infoflow_news_item
```

SQL 位置：

```text
doc/sql/infoflow.sql
```

### 推荐接口

```text
GET  /api/v1/infoflow/repos
GET  /api/v1/infoflow/news
POST /api/v1/infoflow/collect/github
POST /api/v1/infoflow/collect/news
POST /api/v1/infoflow/collect/all
```

接口统一返回：

```java
com.superd.api.response.Response<T>
```

## 本地开发

### 环境要求

- JDK 17
- Maven 3.8+
- MySQL 8+
- 可选：GitHub Personal Access Token

### 配置数据库

根据实际环境配置：

```text
codepath-ai-app/src/main/resources/application-local.yml
```

不要将真实密码和 Token 提交到 Git。

### 初始化 SQL

执行：

```text
doc/sql/infoflow.sql
```

### 编译

```bash
mvn -DskipTests package
```

### 启动

可以通过 IDE 启动：

```text
codepath-ai-app/src/main/java/com/superd/Application.java
```

也可以通过 Maven 启动具体 app 模块，具体命令以本地 Spring Boot 配置为准。

## 开发规范

新增业务必须遵循：

1. 先判断属于哪个领域上下文；
2. 领域层定义 Entity、Port、Repository 和 Domain Service；
3. 基础设施层实现外部访问、DAO 和 Repository；
4. Trigger 层只负责 Controller、Job、Listener；
5. PO 不得出现在 Domain 和 API 返回值中；
6. Controller 不得直接调用 DAO；
7. 外部 HTTP 调用不得写在 Domain；
8. 复用现有 `Response<T>` 和 `ResponseCode`；
9. 先完成最小闭环，再引入缓存、消息队列和 Agent；
10. 所有架构决策记录在 `doc/` 中。

## 演进路线

### Phase 1：信息采集 MVP

```text
GitHub + Hacker News
MySQL + MyBatis
REST + Scheduled Task
```

### Phase 2：Agent Tool 化

将已经稳定的领域服务能力封装为 Spring AI Tool：

```text
collectGitHubTrending
collectIndustryNews
queryLatestRepos
queryLatestNews
```

再通过已有 Armory YAML 配置挂载到 Agent。

### Phase 3：LLM 分析

增加：

- 技术趋势总结；
- 热门项目解释；
- 学习方向建议；
- 结构化报告持久化；
- Prompt 版本管理。

### Phase 4：记忆与个性化

由平台级记忆系统提供：

- 用户兴趣；
- 已读内容；
- 学习进度；
- SSH 服务器历史状态；
- AI IDE 项目上下文。

InfoFlow 只消费统一记忆接口，不独立设计第二套记忆系统。

### Phase 5：更强 Agent 能力

逐步加入：

- Planner Agent；
- 动态工具选择；
- Loop + 质量评估；
- 反思和重试；
- 人机协作确认；
- 跨会话上下文。

## 当前项目的准确定位

项目当前不是一个已经具备完整自主性的通用 Agent，而是：

> 一个以 Java/Spring Boot 为基础、支持 YAML 驱动 Agent 装配、工具扩展和多种工作流编排的开发者 Agent 平台。

这个定位是诚实且可扩展的。后续通过 SSH、AI IDE、记忆、规划和反思能力逐步增强，而不是在没有实现的情况下提前声称具备这些能力。

## 文档

建议维护以下文档：

```text
doc/
├── sql/
├── architecture/
├── agent/
└── infoflow/
```

InfoFlow 开发交接文档包括：

- 项目规范与架构约束；
- MVP 开发计划；
- 本 README 草稿。