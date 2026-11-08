package com.syy.aicodehelper.ai.test.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.syy.aicodehelper.ai.test.entity.ApiDefinition;
import com.syy.aicodehelper.test.executor.HttpExecutionResult;
import com.syy.aicodehelper.test.executor.HttpExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.env.StandardEnvironment;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class HttpExecutorTimeoutTest {
    @Test
    void readTimeoutIsAppliedToRealHttpRequests() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/slow", exchange -> {
            try { Thread.sleep(600); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            byte[] body = "slow".getBytes(StandardCharsets.UTF_8);
            try { exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); }
            finally { exchange.close(); }
        });
        server.start();
        try {
            ApiDefinition api = new ApiDefinition();
            api.setMethod("GET");
            api.setPath("/slow");
            HttpExecutor executor = new HttpExecutor(new RestTemplateBuilder(), new ObjectMapper(),
                    new StandardEnvironment(), 500, 100);
            HttpExecutionResult result = executor.execute(api,
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "{\"pathParams\":{},\"queryParams\":{},\"headers\":{},\"body\":null}");
            assertNotNull(result.transportError());
            assertEquals(0, result.statusCode());
            assertTrue(result.durationMs() < 550, "read timeout should terminate the request near the configured limit");
        } finally {
            server.stop(0);
        }
    }
}
