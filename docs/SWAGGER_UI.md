# Swagger UI 使用说明

## 访问地址

后端默认端口 8081、context-path `/api`：

- UI：`http://localhost:8081/api/swagger-ui/index.html`
- UI 便捷入口：`http://localhost:8081/api/swagger-ui.html`
- OpenAPI JSON：`http://localhost:8081/api/v3/api-docs`

## 使用方法

1. 启动 Spring Boot 后端。
2. 打开 Swagger UI。
3. 按 Controller Tag 浏览接口。
4. 展开接口，点击 `Try it out`。
5. 填写 Path / Query / Header / Body 参数，点击 `Execute`。
6. 上传接口使用 `multipart/form-data` 文件选择框。

## 验收

- `/api/v3/api-docs` 返回 OpenAPI 3 JSON。
- Swagger UI 页面正常加载并显示接口分组。
- `GET /health` 可通过 Try it out 返回 HTTP 200。
- 其他写入类接口请使用测试数据；不要在生产环境随意执行删除或写入请求。

## 配置说明

依赖：`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9`。

项目使用 `server.servlet.context-path=/api`，OpenAPI Server URL 配置为 `/api`，
因此 Try it out 会请求正确的后端路径。
