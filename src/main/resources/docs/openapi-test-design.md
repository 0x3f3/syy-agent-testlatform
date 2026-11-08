# OpenAPI 测试设计知识

## OpenAPI 解析
- 读取 info、servers、paths、HTTP method、operationId、summary、tags、parameters、requestBody、responses、security。
- 同时合并 Path Item 级参数和 Operation 级参数。
- 解析 components/schemas 中的本地 $ref，保留无法解析的外部引用。
- 对每个 method + path 建立独立接口定义。

## 用例生成
- 每个接口至少覆盖一个成功场景、一个无效或缺失参数场景、一个边界场景。
- 对 security 非空的接口考虑未认证和无效令牌场景。
- GET 通常使用 path/query 参数；POST/PUT/PATCH 根据 requestBody schema 和 example 构造 JSON body。
- 测试用例必须包含 apiId、caseName、caseType、priority、request、expected。
- request 结构包含 pathParams、queryParams、headers、body。
- expected 结构包含 statusCode 和 assertions。

## 执行与评估
- HTTP 执行器应记录最终 URL、方法、请求/响应头、响应体、状态码和耗时。
- 4xx/5xx 响应属于有效 HTTP 响应，应继续进入断言流程；网络超时、DNS 或连接失败属于 ERROR。
- 确定性断言包括状态码、JSONPath 存在/相等/包含、响应头相等和响应体包含。
- LLM-as-a-Judge 对响应语义进行二次分析，但不能推翻确定性断言失败。
- PASS/FAIL/ERROR、评分、原因和建议都应持久化到执行记录。
