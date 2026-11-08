package com.syy.testplatform.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 元数据配置。
 *
 * 项目配置了 server.servlet.context-path=/api，因此 Swagger UI 的
 * Try it out 请求需要使用 /api 作为服务前缀。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI syyAgentOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SYY Agent 智能接口自动化测试平台 API")
                        .version("V11.6")
                        .description("面向智能接口自动化测试的 RESTful API。包含 OpenAPI 文档导入、RAG 知识库、Agent 测试用例生成、异步测试任务、并发执行、定时调度、LLM-as-a-Judge 和测试报告。Demo Shop 示例接口仅在 DEMO_API_ENABLED=true 时启用。"))
                .servers(List.of(
                        new Server()
                                .url("/api")
                                .description("本地 Spring Boot API（context-path=/api）")
                ));
    }
}
