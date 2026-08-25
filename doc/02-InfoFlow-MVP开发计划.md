# InfoFlow MVP 开发计划（供后续 Agent 执行）

> 目标：在既有 `codepath-ai` DDD 项目中，实现可靠、可查询、可定时刷新 的计算机行业信息采集能力。
>
> 本阶段不是 Agent 功能开发。请严格控制范围，不加入 LLM、MCP、记忆、用户画像或前端页面。
>
> 本文是范围与设计基线。当前已实现内容、验证记录和待环境验收项请阅读：`doc/infoflow/01-InfoFlow-MVP开发情况.md`。

---

## 1. 已确定范围

### 1.1 本阶段必须完成

```text
1. GitHub 热门项目采集
2. Hacker News 技术行业资讯采集
3. MySQL + MyBatis 持久化
4. REST 查询接口
5. REST 手动采集接口
6. Spring 定时采集任务
7. 可执行 SQL
8. Maven 编译验证
```

### 1.2 本阶段明确不做

```text
- Agent 配置 YAML
- MCP Tool
- LLM 调用与趋势分析
- Prompt
- 会话记忆、长期记忆、向量数据库
- 用户系统和用户画像
- 收藏、点赞、推荐
- WebSocket
- Redis/Kafka/ES
- 前端页面
```

## 2. MVP 数据流

```text
GitHub Search API ──┐
                    ├── Infrastructure Adapter ──┐
Hacker News API ────┘                              │
                                                   ▼
                                          Domain InfoFlowService
                                                   │
                                                   ▼
                                        Repository Adapter / MyBatis
                                                   │
                                                   ▼
                                                MySQL
                                                   │
                                                   ▼
                            REST Controller / Scheduled Job / 后续前端
```

## 3. 数据源设计

### 3.1 GitHub 热门项目

第一版使用 GitHub REST Search API，不使用页面 HTML 爬虫。

接口：

```text
GET https://api.github.com/search/repositories
```

示例查询：

```text
q=created:>=2026-08-01
sort=stars
order=desc
per_page=30
```

“热门”定义：

> 在最近 N 天内创建，按总 Star 数降序返回的开源仓库。

重要说明：

- 这不是 GitHub 网站 `/trending` 页面完全相同的排序；
- 但它是官方、结构稳定、JSON 化、易测试的最小实现；
- 后续若需真正 Trending，可新增另一套 `IGitHubTrendingPort` 实现，而不改领域服务；
- 未认证 GitHub API 限流通常较低，建议在配置中预留可选 Token；
- 请求头至少设置 `Accept: application/vnd.github+json` 和 `User-Agent`。

需要采集字段：

```text
full_name
name
description
html_url
stargazers_count
forks_count
language
topics
created_at
```

### 3.2 Hacker News 行业资讯

第一版使用 Hacker News 官方 Firebase API。

接口：

```text
GET https://hacker-news.firebaseio.com/v0/topstories.json
GET https://hacker-news.firebaseio.com/v0/item/{id}.json
```

执行流程：

```text
1. 请求 topstories，得到 ID 数组；
2. 只取前 30 条，防止串行请求过多；
3. 请求每条 item；
4. 只保存 type=story、有标题的记录；
5. 对没有 url 的 Ask HN 项目，url 使用 Hacker News item 页面；
6. externalId 使用 Hacker News 的 item id；
7. source 固定为 hacker_news。
```

需要采集字段：

```text
id -> external_id
title
url
by -> author
score / descendants（第一版可先不入库）
time -> published_at
```

## 4. DDD 包结构

### 4.1 Domain

```text
codepath-ai-domain/src/main/java/com/superd/domain/infoflow/
├── adapter/
│   ├── port/
│   │   ├── IGitHubTrendingPort.java
│   │   └── IIndustryNewsPort.java
│   └── repository/
│       ├── IHotRepoRepository.java
│       └── INewsItemRepository.java
├── model/
│   ├── entity/
│   │   ├── HotRepoEntity.java
│   │   └── NewsItemEntity.java
│   └── valobj/
│       ├── CollectResultVO.java
│       └── SourceTypeEnum.java
└── service/
    ├── IInfoFlowDomainService.java
    └── InfoFlowService.java
```

### 4.2 Infrastructure

```text
codepath-ai-infrastructure/src/main/java/com/superd/infrastructure/
├── adapter/
│   ├── port/
│   │   ├── GitHubTrendingAdapter.java
│   │   └── HackerNewsAdapter.java
│   └── repository/
│       ├── HotRepoRepository.java
│       └── NewsItemRepository.java
├── dao/
│   ├── IHotRepoDAO.java
│   ├── INewsItemDAO.java
│   └── po/
│       ├── HotRepoPO.java
│       └── NewsItemPO.java
└── gateway/
    └── infoflow/
        ├── GitHubApiClient.java
        ├── HackerNewsApiClient.java
        └── dto/
            ├── GitHubSearchResponseDTO.java
            ├── GitHubRepoDTO.java
            └── HackerNewsItemDTO.java
```

说明：如果第一版采用 Java 原生 `java.net.http.HttpClient`，可以直接将 HTTP 实现放在 Adapter 中，不强制创建独立 ApiClient。关键约束是：**外部 HTTP 请求实现必须在 infrastructure 层。**

### 4.3 Trigger

```text
codepath-ai-trigger/src/main/java/com/superd/trigger/
├── http/
│   └── InfoFlowController.java
└── job/
    └── InfoFlowCollectJob.java
```

### 4.4 API

为了复用现有分层，推荐添加 API interface 和 DTO：

```text
codepath-ai-api/src/main/java/com/superd/api/
├── IInfoFlowService.java
└── dto/
    ├── HotRepoResponseDTO.java
    ├── NewsItemResponseDTO.java
    └── CollectResponseDTO.java
```

如果后续 Agent 判断当前 API module 尚未稳定，也可以先让 Controller 直接返回 Domain Entity；但在项目规范中优先使用 DTO，避免 Entity 泄漏到接口层。

### 4.5 App Resources

```text
codepath-ai-app/src/main/resources/
├── mybatis/mapper/
│   ├── infoflow_hot_repo_mapper.xml
│   └── infoflow_news_item_mapper.xml
└── application-local.yml
```

### 4.6 SQL

```text
codepath-ai/doc/sql/infoflow.sql
```

## 5. 领域模型建议

### 5.1 HotRepoEntity

```java
public class HotRepoEntity {
    private Long id;
    private String fullName;
    private String name;
    private String description;
    private String htmlUrl;
    private Integer stars;
    private Integer forks;
    private String language;
    private String topics;
    private String source;
    private LocalDateTime publishedAt;
    private LocalDateTime collectedAt;
}
```

业务默认值：

```text
source = github
collectedAt = 当前时间
```

### 5.2 NewsItemEntity

```java
public class NewsItemEntity {
    private Long id;
    private String externalId;
    private String title;
    private String url;
    private String author;
    private String summary;
    private String source;
    private LocalDateTime publishedAt;
    private LocalDateTime collectedAt;
}
```

业务默认值：

```text
source = hacker_news
collectedAt = 当前时间
```

### 5.3 CollectResultVO

用于手动采集接口和任务日志。

```java
public class CollectResultVO {
    private String source;
    private Integer fetchedCount;
    private Integer savedCount;
    private boolean success;
    private String message;
    private LocalDateTime collectedAt;
}
```

## 6. Domain 接口设计

```java
public interface IGitHubTrendingPort {
    List<HotRepoEntity> fetchTrending();
}
```

```java
public interface IIndustryNewsPort {
    List<NewsItemEntity> fetchTopStories();
}
```

```java
public interface IHotRepoRepository {
    int saveOrUpdate(List<HotRepoEntity> entities);
    List<HotRepoEntity> queryLatest(String language, int limit);
}
```

```java
public interface INewsItemRepository {
    int saveIgnoreDuplicate(List<NewsItemEntity> entities);
    List<NewsItemEntity> queryLatest(String source, int limit);
}
```

```java
public interface IInfoFlowDomainService {
    CollectResultVO collectGitHubTrending();
    CollectResultVO collectIndustryNews();
    List<HotRepoEntity> queryHotRepos(String language, int limit);
    List<NewsItemEntity> queryNews(String source, int limit);
}
```

## 7. 数据库设计

### 7.1 GitHub 热门仓库表

```sql
CREATE TABLE IF NOT EXISTS infoflow_hot_repo (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    repo_full_name VARCHAR(255) NOT NULL COMMENT '仓库全名，owner/repo',
    repo_name VARCHAR(255) NOT NULL COMMENT '仓库名',
    description TEXT NULL COMMENT '仓库描述',
    html_url VARCHAR(500) NOT NULL COMMENT 'GitHub 地址',
    stars INT NOT NULL DEFAULT 0 COMMENT '总 Star 数',
    forks INT NOT NULL DEFAULT 0 COMMENT 'Fork 数',
    language VARCHAR(64) NULL COMMENT '主要语言',
    topics VARCHAR(1000) NULL COMMENT '标签，逗号分隔',
    source VARCHAR(64) NOT NULL DEFAULT 'github' COMMENT '来源',
    published_at DATETIME NULL COMMENT '仓库创建时间',
    collected_date DATE NOT NULL COMMENT '采集日期，用于当日快照去重',
    collected_at DATETIME NOT NULL COMMENT '采集时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_repo_collected_date (repo_full_name, collected_date),
    KEY idx_collected_at (collected_at),
    KEY idx_language_collected_at (language, collected_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='InfoFlow GitHub 热门仓库快照';
```

写入策略建议：

```sql
INSERT ... ON DUPLICATE KEY UPDATE
    description = VALUES(description),
    stars = VALUES(stars),
    forks = VALUES(forks),
    language = VALUES(language),
    topics = VALUES(topics),
    collected_at = VALUES(collected_at);
```

理由：同一仓库同一天重复采集时更新星标数据，不重复插入。

### 7.2 Hacker News 资讯表

```sql
CREATE TABLE IF NOT EXISTS infoflow_news_item (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    external_id VARCHAR(128) NOT NULL COMMENT '外部平台文章 ID，例如 Hacker News item id',
    title VARCHAR(1000) NOT NULL COMMENT '标题',
    url VARCHAR(1000) NOT NULL COMMENT '原文或站内地址',
    author VARCHAR(255) NULL COMMENT '作者',
    summary TEXT NULL COMMENT '摘要，MVP 可为空',
    source VARCHAR(64) NOT NULL COMMENT '来源',
    published_at DATETIME NULL COMMENT '原发布时间',
    collected_at DATETIME NOT NULL COMMENT '采集时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_source_external_id (source, external_id),
    KEY idx_source_published_at (source, published_at),
    KEY idx_collected_at (collected_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='InfoFlow 行业资讯';
```

写入策略建议：

```sql
INSERT IGNORE
```

理由：Hacker News item 的外部 ID 全局稳定；MVP 中一条资讯只保存一次即可。

## 8. 配置设计

在 `application-local.yml` 加入：

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
  schedule:
    github-cron: "0 0 0 */6 * ?"
    news-cron: "0 0 0 */2 * ?"
```

说明：

- 不要在 YAML 写入真实 GitHub Token；
- `GITHUB_TOKEN` 通过系统环境变量或 `.env` / IDE Run Configuration 注入；
- GitHub Token 不是 MVP 必需项，但应兼容；
- 没有 Token 也应能工作，只是 API 限流更严格。

为配置增加：

```java
@ConfigurationProperties(prefix = "infoflow")
```

配置类应位于 infrastructure 或 app config 层，不放在 domain。

## 9. REST API 设计

沿用项目规范：

```text
/api/v1/
Response<T>
```

### 9.1 查询热门仓库

```text
GET /api/v1/infoflow/repos?language=Java&limit=20
```

- `language`：可选；
- `limit`：可选，默认 20，最大值限制为 100；
- 按 `collected_at DESC, stars DESC` 排序；
- 返回 `Response<List<HotRepoResponseDTO>>`。

### 9.2 查询行业资讯

```text
GET /api/v1/infoflow/news?source=hacker_news&limit=20
```

- `source`：可选；
- `limit`：可选，默认 20，最大 100；
- 按 `published_at DESC` 排序；
- 返回 `Response<List<NewsItemResponseDTO>>`。

### 9.3 手动采集 GitHub

```text
POST /api/v1/infoflow/collect/github
```

返回：

```text
Response<CollectResponseDTO>
```

### 9.4 手动采集行业资讯

```text
POST /api/v1/infoflow/collect/news
```

### 9.5 手动全量采集

```text
POST /api/v1/infoflow/collect/all
```

执行顺序：

```text
GitHub → Hacker News
```

MVP 可同步执行。若外部 API 失败，返回对应来源的错误结果；不要静默吞错。

## 10. 定时任务设计

在启动类或已有配置上确认启用：

```java
@EnableScheduling
```

任务类：

```java
@Component
@Slf4j
public class InfoFlowCollectJob {

    @Scheduled(cron = "${infoflow.schedule.github-cron}")
    public void collectGitHubTrending() {
        // 调用领域服务；记录成功或失败
    }

    @Scheduled(cron = "${infoflow.schedule.news-cron}")
    public void collectIndustryNews() {
        // 调用领域服务；记录成功或失败
    }
}
```

注意：

- 定时任务必须只调用领域服务，不能在 Job 内写 HTTP 逻辑；
- 定时任务异常必须 catch 并记录，防止调度线程异常影响下一轮；
- 手动接口与定时任务必须复用相同领域服务；
- 不引入 Quartz、XXL-Job、分布式锁，单机 MVP 不需要。

## 11. 推荐开发顺序

后续 Agent 必须按以下顺序工作：

1. 阅读 `01-项目规范与架构约束.md`；
2. 检查 `pom.xml`、已有 SSH 领域和 Mapper，确认未发生架构变动；
3. 创建 SQL 文件，但不要默认执行生产数据库；
4. 创建 Domain Entity、Port、Repository 接口、Domain Service；
5. 创建 PO、DAO、Mapper XML；
6. 创建 Repository Adapter，完成 Entity/PO 转换；
7. 创建 GitHub 与 Hacker News 外部适配器；
8. 创建配置属性类与 HTTP 客户端；
9. 创建 API DTO / API Interface；
10. 创建 Controller；
11. 创建定时任务；
12. 添加或确认 `@EnableScheduling`；
13. 执行 `mvn test` 或至少 `mvn -DskipTests package`；
14. 启动项目并手动调用采集 API；
15. 查询数据库和 GET API 验证数据闭环；
16. 更新 README 的 InfoFlow 部分。

## 12. 验收标准

### 12.1 编译

```text
mvn -DskipTests package
```

父工程构建成功，且没有因为 InfoFlow 产生编译错误。

### 12.2 GitHub 采集

```text
POST /api/v1/infoflow/collect/github
```

期望：

- HTTP 成功；
- Response code 为 SUCCESS；
- savedCount 大于 0（网络正常且 API 未限流时）；
- 表 `infoflow_hot_repo` 有数据；
- 再次调用不会产生相同仓库、相同日期的重复记录；
- 同一天重复采集时 star 数据可更新。

### 12.3 Hacker News 采集

```text
POST /api/v1/infoflow/collect/news
```

期望：

- `infoflow_news_item` 写入数据；
- `source=hacker_news`；
- `external_id` 为 Hacker News item ID；
- 再次调用没有重复行。

### 12.4 查询接口

```text
GET /api/v1/infoflow/repos?limit=20
GET /api/v1/infoflow/news?limit=20
```

期望：

- 返回统一 `Response<T>`；
- 返回 DTO，不暴露 PO；
- `limit` 非法时进行边界保护；
- 空库时返回成功空列表，不抛异常。

### 12.5 失败处理

需要验证至少一种失败情况：

- GitHub API 403 限流；
- 网络不可达；
- Hacker News item 不存在或字段缺失。

期望：

- 有清晰日志；
- Controller 返回可理解错误；
- 不写入脏数据；
- 一个来源失败不影响另一个来源的手动采集。

## 13. 后续 Agent 化演进（仅预留，不实现）

MVP 完成并稳定后，InfoFlow 才可按以下顺序演进：

```text
Phase 2：将 IInfoFlowDomainService 的能力封装为 Spring AI @Tool
        ↓
Phase 3：将 Tool 挂载进现有 Armory YAML Agent 配置
        ↓
Phase 4：接入用户记忆系统，让 Agent 按历史偏好筛选和解释信息
        ↓
Phase 5：增加 LLM 趋势总结、学习路径建议和报告持久化
```

原则：

> Agent、Memory、LLM 是消费 InfoFlow 基础数据能力的上层；它们不能反向污染 MVP 的领域采集和持久化模型。

