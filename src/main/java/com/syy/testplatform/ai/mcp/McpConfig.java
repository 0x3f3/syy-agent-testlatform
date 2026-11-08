package com.syy.testplatform.ai.mcp;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@ConditionalOnProperty(prefix = "app.mcp", name = "enabled", havingValue = "true")
public class McpConfig {

    @Value("${bigmodel.api-key:}")
    private String apiKey;

    @Bean
    public McpToolProvider mcpToolProvider() {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("MCP 已启用，但 BIGMODEL_API_KEY 未配置");
        }

        McpTransport transport = new HttpMcpTransport.Builder()
                .sseUrl("https://open.bigmodel.cn/api/mcp/web_search/sse?Authorization=" + apiKey)
                // 避免请求/响应日志泄露凭据或业务数据。
                .logRequests(false)
                .logResponses(false)
                .build();

        McpClient mcpClient = new DefaultMcpClient.Builder()
                .key("syyMcpClient")
                .transport(transport)
                .build();

        return McpToolProvider.builder()
                .mcpClients(mcpClient)
                .build();
    }
}
