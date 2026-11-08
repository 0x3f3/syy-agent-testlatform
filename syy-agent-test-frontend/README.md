# SYY Agent Test Platform 前端

Vue 3 + Vite 单页应用，提供测试总览、OpenAPI 接口管理、Agent + RAG 用例工作台、HTTP 测试任务、执行明细和 ECharts 报告。

```bash
npm install
npm run dev
```

默认在 `http://localhost:5173` 启动，`/api` 请求由 Vite 代理到 `http://127.0.0.1:8081`。可通过 `VITE_API_BASE_URL` 覆盖 API 前缀。ECharts 通过 jsDelivr CDN 加载，报告图表需要能够访问该 CDN。
