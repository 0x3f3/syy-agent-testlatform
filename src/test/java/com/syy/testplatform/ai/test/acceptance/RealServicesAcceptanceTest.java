package com.syy.testplatform.ai.test.acceptance;

import com.sun.net.httpserver.HttpServer;
import com.syy.testplatform.ai.test.entity.ApiDefinition;
import com.syy.testplatform.ai.test.entity.ApiDocument;
import com.syy.testplatform.ai.test.entity.TestCase;
import com.syy.testplatform.ai.test.entity.TestExecution;
import com.syy.testplatform.ai.test.repository.ApiDefinitionRepository;
import com.syy.testplatform.ai.test.repository.ApiDocumentRepository;
import com.syy.testplatform.ai.test.repository.KnowledgeChunkRepository;
import com.syy.testplatform.ai.test.repository.TestCaseRepository;
import com.syy.testplatform.ai.test.repository.TestExecutionRepository;
import com.syy.testplatform.ai.test.repository.TestReportRepository;
import com.syy.testplatform.ai.test.service.CreateTestTaskRequest;
import com.syy.testplatform.ai.test.service.RedisPlatformCache;
import com.syy.testplatform.ai.test.service.TaskProgressResponse;
import com.syy.testplatform.ai.test.service.TaskRunResponse;
import com.syy.testplatform.ai.test.service.TaskSubmissionResponse;
import com.syy.testplatform.ai.test.entity.TestTask;
import com.syy.testplatform.ai.test.entity.TestReport;
import com.syy.testplatform.ai.test.service.TestTaskService;
import com.syy.testplatform.ai.test.rag.KnowledgeBaseService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Opt-in real dependency acceptance test. It requires reachable MySQL, Redis and a valid DashScope key.
 * Enable with RUN_EXTERNAL_ACCEPTANCE=true; this test deliberately does not mock Qwen, Redis or MySQL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "app.execution.per-task-concurrency=3",
        "app.execution.http-core-pool-size=3",
        "app.execution.http-max-pool-size=3",
        "app.execution.http-queue-capacity=30",
        "app.redis.cache-enabled=true",
        "app.scheduler.enabled=false"
})
@EnabledIfEnvironmentVariable(named = "RUN_EXTERNAL_ACCEPTANCE", matches = "true")
class RealServicesAcceptanceTest {
    private static HttpServer fixture;
    private static ExecutorService fixturePool;
    private static int fixturePort;
    private static final AtomicInteger ACTIVE = new AtomicInteger();
    private static final AtomicInteger MAX_ACTIVE = new AtomicInteger();

    @Autowired JdbcTemplate jdbc;
    @Autowired RedisPlatformCache redisCache;
    @Autowired KnowledgeBaseService knowledgeBase;
    @Autowired ApiDocumentRepository documentRepository;
    @Autowired ApiDefinitionRepository apiRepository;
    @Autowired TestCaseRepository caseRepository;
    @Autowired TestExecutionRepository executionRepository;
    @Autowired TestReportRepository reportRepository;
    @Autowired KnowledgeChunkRepository knowledgeRepository;
    @Autowired TestTaskService taskService;

    @BeforeAll
    static void startFixture() throws Exception {
        fixture = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fixturePool = Executors.newFixedThreadPool(8);
        fixture.setExecutor(fixturePool);
        fixture.createContext("/probe", exchange -> {
            int active = ACTIVE.incrementAndGet();
            MAX_ACTIVE.accumulateAndGet(active, Math::max);
            try {
                try {
                    Thread.sleep(350);
                } catch (InterruptedException interrupted) {
                    // Preserve the interrupt signal and close this fixture exchange cleanly.
                    Thread.currentThread().interrupt();
                    exchange.close();
                    return;
                }
                byte[] response = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (var output = exchange.getResponseBody()) { output.write(response); }
            } finally {
                ACTIVE.decrementAndGet();
            }
        });
        fixture.start();
        fixturePort = fixture.getAddress().getPort();
    }

    @AfterAll
    static void stopFixture() {
        if (fixture != null) fixture.stop(0);
        if (fixturePool != null) fixturePool.shutdownNow();
    }

    @Test
    void verifiesRealMysqlRedisQwenConcurrentHttpExecutionAndAiBatchReport() throws Exception {
        assertEquals(1, jdbc.queryForObject("select 1", Integer.class), "MySQL must be reachable");
        assertTrue(redisCache.ping(), "Redis must answer PING");

        String source = "acceptance:v11:" + System.currentTimeMillis();
        knowledgeBase.indexText(source, "验收测试知识 " + source + "：接口测试应覆盖正常、异常和边界场景。失败结果需要记录证据并生成修复建议。");
        String retrieved = knowledgeBase.search("验收测试知识 " + source + " 接口测试的正常异常边界覆盖和失败报告", 3);
        assertTrue(retrieved.contains("验收测试知识"), "Qwen embedding and MySQL RAG retrieval must work");

        ApiDocument document = new ApiDocument();
        document.setName("v11-real-acceptance.json");
        document.setTitle("V11 Real Acceptance");
        document.setVersion("1.0");
        document.setSpecificationVersion("OpenAPI 3.0.3");
        document.setServerUrl("http://127.0.0.1:" + fixturePort);
        document.setRawDocument("{}");
        document.setImportedAt(LocalDateTime.now());
        document = documentRepository.save(document);

        ApiDefinition api = new ApiDefinition();
        api.setDocumentId(document.getId());
        api.setPath("/probe");
        api.setMethod("GET");
        api.setDescription("Concurrent acceptance probe");
        api.setServerUrl(document.getServerUrl());
        apiRepository.save(api);

        for (int i = 1; i <= 3; i++) {
            TestCase testCase = new TestCase();
            testCase.setApiId(api.getId());
            testCase.setCaseName("并发验收用例 " + i);
            testCase.setCaseType(i == 1 ? "POSITIVE" : i == 2 ? "NEGATIVE" : "BOUNDARY");
            testCase.setPriority("P1");
            testCase.setDescription("真实 HTTP 请求、Qwen Judge 和批量报告验收样本");
            testCase.setRequestJson("{\"pathParams\":{},\"queryParams\":{\"id\":" + i + "},\"headers\":{},\"body\":null}");
            testCase.setExpectResult("{\"statusCode\":200,\"assertions\":[{\"type\":\"JSON_PATH_EQUALS\",\"expression\":\"$.status\",\"expectedValue\":\"ok\"}]}");
            testCase.setAssertionsJson("[]");
            testCase.setEnabled(true);
            caseRepository.save(testCase);
        }

        TaskSubmissionResponse accepted = taskService.submit(new CreateTestTaskRequest(
                document.getId(), "V11 真实服务验收", document.getServerUrl(), null));
        assertNotNull(accepted.taskId());
        long deadline = System.currentTimeMillis() + 120000;
        TaskProgressResponse progress;
        do {
            progress = taskService.getProgress(accepted.taskId());
            if ("COMPLETED".equals(progress.status()) || "FAILED".equals(progress.status())) break;
            Thread.sleep(250);
        } while (System.currentTimeMillis() < deadline);
        assertEquals("COMPLETED", progress.status(), "async real-service task should finish");
        TestTask completedTask = taskService.getTask(accepted.taskId());
        List<TestExecution> completedExecutions = taskService.executions(accepted.taskId());
        TestReport completedReport = taskService.reportByTask(accepted.taskId()).orElseThrow();
        TaskRunResponse run = new TaskRunResponse(completedTask, completedExecutions, completedReport);

        assertEquals("COMPLETED", run.task().getStatus());
        assertEquals(3, run.executions().size());
        assertEquals(3, run.report().getTotalCount());
        assertTrue(MAX_ACTIVE.get() >= 2, "real HTTP requests should overlap under configured concurrency");
        assertTrue(run.executions().stream().allMatch(e -> Boolean.TRUE.equals(e.getJudgeAvailable())),
                "real Qwen LLM-as-a-Judge should be available for each case");
        assertTrue(run.report().getAiAnalysis().contains("整批测试 AI 智能总结"),
                "batch report must come from the real report Agent, not fallback summary");

        TaskProgressResponse cachedProgress = redisCache.getTaskProgress(run.task().getId()).orElseThrow();
        assertEquals(3, cachedProgress.completedCount());
        assertTrue(cachedProgress.reportAvailable());

        // Clean only rows created by this acceptance test; never truncate shared project tables.
        reportRepository.deleteAll(reportRepository.findByTaskId(run.task().getId()).stream().toList());
        executionRepository.deleteAll(executionRepository.findByTaskIdOrderByExecuteTimeAsc(run.task().getId()));
        jdbc.update("delete from test_task where id = ?", run.task().getId());
        caseRepository.deleteAll(caseRepository.findByApiIdOrderByIdAsc(api.getId()));
        apiRepository.deleteById(api.getId());
        documentRepository.deleteById(document.getId());
        knowledgeRepository.deleteBySource(source);
    }
}
