# SYY Agent 智能接口自动化测试平台 · V11 岗位要求完整交付版

基于 Java 21、Spring Boot 3.5、LangChain4j、Qwen、MySQL、Flyway、Redis、Vue 3 和 ECharts 构建。V11 在 V10 的 Agent + RAG 测试链路上补充了 Redis 缓存、异步任务、并发 HTTP 执行、Cron 定时调度和 OpenAPI YAML 解析。

> **验收状态说明**：代码包包含单元测试、H2 服务级集成测试，以及一个不 mock MySQL / Redis / Qwen 的真实依赖验收测试。真实依赖验收测试需要用户自己的 MySQL、Redis 和 DashScope 凭据。本制作环境没有 Maven 发行包下载网络、MySQL、Redis 服务或 DashScope Key，因此不能声称真实外部依赖验收已通过；请按“验收测试”章节执行后再标记最终完成。

## 技术栈

- 后端：Java 21、Spring Boot 3.5、LangChain4j 1.1、Qwen / DashScope
- 测试能力：OpenAPI 3.x / Swagger 2.0 JSON/YAML、RAG、HTTP Executor、LLM-as-a-Judge、整批 AI 报告
- 持久化：MySQL、Spring Data JPA、Flyway V1–V3
- 缓存：Redis（接口文档、RAG 检索结果、任务进度；分别配置 TTL）
- 任务：Spring `@Async` 后台任务池、受限 HTTP 并发池、Spring Cron 定时调度
- 前端：Vue 3、Vite、ECharts

## 快速启动

### 1. 准备环境

需要 JDK 21、Docker Compose（或独立 MySQL 8 / Redis 7）、可联网 Maven 环境，以及可用的 DashScope API Key。

```powershell
Copy-Item .env.example .env
notepad .env
```

填写 `DB_PASSWORD` 和 `DASHSCOPE_API_KEY`。如果使用 docker compose，`.env` 中的数据库密码会被 Compose 使用：

```bash
docker compose up -d mysql redis
```

Docker MySQL 默认映射到宿主机 `3307`（容器内仍为 `3306`），Redis 默认端口 `6379`。这样可避开 Windows 本机已有 MySQL 占用 `3306` 的常见冲突。若 `.env` 是旧版本复制过来的，请将 `DB_PORT=3306` 改为 `DB_PORT=3307`，确保后端连接到 Docker MySQL。生产环境请自行设置强密码、限制网络访问并使用持久化卷。

### 2. 启动后端

```powershell
.\run-local.ps1
```

或：

```bash
./mvnw spring-boot:run
```

后端地址：`http://localhost:8081/api`。首次启动由 Flyway 自动执行 V1、V2、V3。升级旧库前请备份；Hibernate `ddl-auto` 默认 `none`，不要改回 `update`。

### 3. 启动前端

```bash
cd syy-agent-test-frontend
npm install
npm run dev
```

前端地址：`http://localhost:5173`。支持 `.json`、`.yaml`、`.yml` 文档上传。

## V11 功能和验收点

### 1. Redis 缓存

| 缓存内容 | Redis Key 前缀 | 默认 TTL | 失效策略 |
|---|---|---:|---|
| OpenAPI 文档元数据及原始契约 | `syy:v11:document:` | 30 分钟 | 过期后回源 MySQL |
| RAG 检索结果 | `syy:v11:rag:` | 10 分钟 | 知识库更新时递增版本键，旧缓存自然失效 |
| 任务进度 | `syy:v11:task-progress:` | 24 小时 | 任务状态更新时覆盖，过期后回源 MySQL |

Redis 不可用时，业务会记录缓存降级并回源 MySQL / 重新检索；Redis 不作为唯一数据源。可通过 `REDIS_CACHE_ENABLED=false` 关闭缓存。

### 2. 异步任务

- `POST /api/test/tasks` 先持久化 `QUEUED` 任务并立即返回 `taskId`。
- 后台任务线程池负责用例生成、执行和报告生成。
- `GET /api/test/tasks/{id}/progress` 返回状态、总数、已完成数、PASS/FAIL/ERROR、进度百分比和报告可用状态。
- `GET /api/test/tasks/{id}` 查询任务，`GET /api/test/tasks/{id}/executions` 查询明细，`GET /api/test/tasks/{id}/report` 查询报告。
- 队列容量可配置；任务队列饱和时明确拒绝并记录失败状态，不静默丢任务。

### 3. HTTP 并发执行

- 每个任务最大并发数由 `PER_TASK_CONCURRENCY` 控制，默认 4。
- HTTP 线程池全局 core/max/queue 可配置，默认 8/16/500。
- 连接超时、读取超时分别由 `HTTP_CONNECT_TIMEOUT_MS` / `HTTP_READ_TIMEOUT_MS` 控制，默认 5s / 15s。
- 单条用例的异常隔离：失败用例记录为 ERROR，不中断同批次其他用例。
- HTTP 并发池使用有界队列和背压策略，不会无限创建线程。

### 4. 定时任务调度

- `POST /api/test/schedules` 创建计划，`GET /api/test/schedules` 查询。
- `PATCH /api/test/schedules/{id}/enabled` 启用/暂停，`DELETE /api/test/schedules/{id}` 删除。
- 使用 Spring 六段 Cron 表达式（秒 分 时 日 月 周），默认时区 `Asia/Shanghai`。
- 调度器每 15 秒扫描到期计划；先通过数据库行锁 claim 到期记录，再在事务提交后提交异步任务，避免多实例重复触发。
- 默认示例：每天上午 9 点 `0 0 9 * * *`；每 30 分钟 `0 0/30 * * * *`。

### 5. JSON/YAML 解析

- 支持 Swagger 2.0 和 OpenAPI 3.x JSON/YAML。
- 仍保留接口参数合并、本地 `$ref` 解析、请求体 Schema、示例、响应和安全配置提取。
- 上传扩展名支持 `.json`、`.yaml`、`.yml`。

### 6. 数据库迁移

- V1：初始化平台表结构。
- V2：补齐旧版表列、指纹及索引。
- V3：为 `test_task` 增加异步任务请求快照，并创建 `test_schedule` 定时计划表。
- 迁移脚本只增量添加列和表，不删除历史业务数据。生产升级前必须备份数据库。

## 常用 API

| 方法 | 路由 | 说明 |
|---|---|---|
| POST | `/api/test/swagger/upload` | 上传 JSON/YAML OpenAPI |
| GET | `/api/test/documents` | 查询文档 |
| GET | `/api/test/documents/{id}/coverage` | 查询正常/异常/边界覆盖 |
| POST | `/api/test/documents/{id}/generate-cases` | Agent + RAG 生成用例 |
| POST | `/api/test/tasks` | 提交异步测试任务，立即返回 taskId |
| GET | `/api/test/tasks/{id}/progress` | 查询异步任务进度 |
| GET | `/api/test/tasks/{id}/executions` | 查询执行明细 |
| GET | `/api/test/tasks/{id}/report` | 查询整批报告 |
| POST | `/api/test/schedules` | 创建 Cron 定时计划 |
| GET | `/api/test/schedules` | 查询定时计划 |
| PATCH | `/api/test/schedules/{id}/enabled` | 启用/暂停计划 |
| DELETE | `/api/test/schedules/{id}` | 删除计划 |

## 验收测试

### A. 本地自动测试（不需要真实 DashScope Key）

项目包含：

- `OpenApiYamlParserTest`：YAML 和 JSON 解析。
- `BoundedConcurrentExecutorTest`：验证最大并发数限制及单用例失败隔离。
- `CronScheduleTest`：验证 Cron 计算和非法表达式拒绝。
- `ApiTestingEndToEndIntegrationTest`：H2 + 本地 HTTP fixture，验证 Flyway、导入、用例覆盖/去重、同步兼容入口和异步任务链路；Agent / Judge / Report / RAG 在该测试中使用 mock。
- `FlywayLegacyUpgradeTest`：验证旧库 baseline 后升级 V1–V3 且保留旧数据。

执行：

```powershell
.\mvnw.cmd clean test
```

### B. 真实依赖验收（必须执行，才可标记“全部完成”）

`RealServicesAcceptanceTest` 不 mock MySQL、Redis、Qwen Embedding、LLM Judge 或整批报告 Agent。它验证：

1. MySQL 实际连接与 V1–V3 迁移。
2. Redis PING、任务进度缓存读写。
3. Qwen Embedding、MySQL RAG 索引与检索。
4. 本地 HTTP fixture 的真实并发请求和单任务并发上限。
5. 真实 Qwen LLM-as-a-Judge。
6. 真实整批 Report Agent 输出，而不是规则 fallback。

先启动 MySQL / Redis，填写 `.env` 的真实 DashScope Key，然后在 PowerShell 设置：

```powershell
$env:RUN_EXTERNAL_ACCEPTANCE = "true"
.\mvnw.cmd -Dtest=RealServicesAcceptanceTest test
```

该验收会向配置的 MySQL 写入临时数据并在成功结束时清理测试行。请使用专用测试库，不要指向生产数据库。验收失败时检查控制台的具体断言、数据库权限、Redis 连接和 DashScope 模型权限。

### C. 前端构建

```powershell
cd syy-agent-test-frontend
npm ci
npm run build
```

## 重要配置

| 环境变量 | 默认值 | 作用 |
|---|---:|---|
| `REDIS_HOST` / `REDIS_PORT` | `localhost:6379` | Redis 地址 |
| `REDIS_CACHE_ENABLED` | `true` | 是否启用 Redis 缓存 |
| `REDIS_DOCUMENT_TTL` | `30m` | 文档缓存 TTL |
| `REDIS_RAG_TTL` | `10m` | RAG 缓存 TTL |
| `REDIS_TASK_STATUS_TTL` | `24h` | 任务进度 TTL |
| `PER_TASK_CONCURRENCY` | `4` | 单任务并发用例数 |
| `HTTP_CORE_POOL_SIZE` / `HTTP_MAX_POOL_SIZE` | `8` / `16` | HTTP 全局线程池 |
| `HTTP_CONNECT_TIMEOUT_MS` | `5000` | HTTP 连接超时 |
| `HTTP_READ_TIMEOUT_MS` | `15000` | HTTP 读取超时 |
| `SCHEDULER_ENABLED` | `true` | 是否启用定时调度 |
| `SCHEDULER_POLL_INTERVAL_MS` | `15000` | 调度扫描间隔 |
| `SCHEDULER_ZONE` | `Asia/Shanghai` | Cron 时区 |

## 安全提示

- DashScope Key 只保存在后端 `.env` / 环境变量，不能放入 `VITE_*` 前端变量。
- `.env` 已加入 `.gitignore`，不要上传或提交真实凭据。
- 目标服务 Base URL 应仅指向你有权测试的系统；调度频率和并发数需要遵守目标环境限流策略。
- MySQL 是业务数据源，Redis 是可丢失缓存；Redis 缓存不可用时不应导致已持久化任务数据丢失。


## Swagger UI（V11.5 新增）

后端启动后可直接在浏览器查看和调试本项目 RESTful API：

- Swagger UI：`http://localhost:8081/api/swagger-ui/index.html`
- 便捷入口：`http://localhost:8081/api/swagger-ui.html`
- OpenAPI JSON：`http://localhost:8081/api/v3/api-docs`

Swagger UI 已按 Controller 分组，包含 Agent、OpenAPI 文档管理、RAG、
异步任务、定时调度、测试报告、健康检查等接口。启用
`DEMO_API_ENABLED=true` 时，也会展示 Demo Shop 示例接口。

Swagger UI 的 “Try it out” 使用 `/api` 作为服务前缀。若后端端口或
`server.servlet.context-path` 被修改，请同步调整 `OpenApiConfig` 中的
Server URL。

### Swagger UI 验收

1. 启动后端。
2. 打开 `http://localhost:8081/api/v3/api-docs`，应返回 OpenAPI JSON。
3. 打开 `http://localhost:8081/api/swagger-ui/index.html`，应显示接口分组。
4. 展开“健康检查”，执行 `GET /health`，预期返回 HTTP 200。
5. 对需要文件的接口（例如 Swagger 上传），在 Swagger UI 中选择文件后点击 Execute。


## V11.6 动态 Swagger、RAG 与 Agent 可观测性

新增动态 Swagger 文档中心：

- `http://localhost:8081/api/swagger-documents.html`
- 平台自身 OpenAPI：`http://localhost:8081/api/v3/api-docs`
- 已上传文档原始内容：`GET /api/test/documents/{id}/raw`

Swagger 文档中心会从 MySQL 文档列表动态构建选择器，支持在平台 REST API 和上传的 JSON/YAML 文档之间切换。原始文档保持 JSON/YAML 格式并在持久化前脱敏。

RAG 诊断接口：

- `GET /api/test/knowledge/search?query=...&topK=5`

Agent 诊断接口：

- `POST /api/test/documents/{documentId}/generate-cases/diagnostics`

完整测试步骤见 `docs/RAG_AGENT_SWAGGER_ACCEPTANCE.md`。
