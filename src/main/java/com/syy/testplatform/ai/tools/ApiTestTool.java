package com.syy.testplatform.ai.tools;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * HTTP接口测试工具
 * 提供给Agent调用，用于真实执行接口测试
 */
@Component
public class ApiTestTool {

    private final RestClient client = RestClient.create();

    @Tool("执行HTTP接口测试请求")
    public String execute(String url, String method, String body) {
        try {
            if ("GET".equalsIgnoreCase(method)) {
                return client.get().uri(url).retrieve().body(String.class);
            }
            return client.post()
                    .uri(url)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            return "接口调用失败:" + e.getMessage();
        }
    }
}
