package com.syy.testplatform.ai.test.integration;

import com.syy.testplatform.ai.agent.AiTestEvaluatorAgent;
import com.syy.testplatform.ai.agent.AiTestReportAgent;
import com.syy.testplatform.ai.test.entity.TestCase;
import com.syy.testplatform.ai.test.repository.TestExecutionRepository;
import com.syy.testplatform.ai.test.repository.TestCaseRepository;
import com.syy.testplatform.ai.test.repository.TestReportRepository;
import com.syy.testplatform.ai.test.service.ApiImportResult;
import com.syy.testplatform.ai.test.service.CreateTestTaskRequest;
import com.syy.testplatform.ai.test.service.ScenarioCoverageResponse;
import com.syy.testplatform.ai.test.service.TaskRunResponse;
import com.syy.testplatform.ai.test.service.TestCaseGenerationService;
import com.syy.testplatform.ai.test.service.TestPlatformService;
import com.syy.testplatform.ai.test.service.TestTaskService;
import com.syy.testplatform.ai.test.schedule.CreateTestScheduleRequest;
import com.syy.testplatform.ai.test.schedule.TestScheduleService;
import com.syy.testplatform.ai.test.rag.KnowledgeBaseService;
import com.syy.testplatform.test.agent.TestCaseGenerateAgent;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Full service-level E2E: Flyway -> OpenAPI import -> Agent generation -> HTTP execution -> Judge -> batch report. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.url=jdbc:h2:mem:syy-e2e;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=true",
        "spring.flyway.baseline-version=0",
        "spring.jpa.hibernate.ddl-auto=none",
        "app.mcp.enabled=false",
        "app.redis.cache-enabled=false",
        "app.scheduler.enabled=false",
        "langchain4j.community.dashscope.chat-model.api-key=test-key",
        "langchain4j.community.dashscope.chat-model.model-name=qwen-turbo",
        "langchain4j.community.dashscope.embedding-model.api-key=test-key",
        "langchain4j.community.dashscope.embedding-model.model-name=text-embedding-v4",
        "langchain4j.community.dashscope.streaming-chat-model.api-key=test-key",
        "langchain4j.community.dashscope.streaming-chat-model.model-name=qwen-turbo",
        "demo.shop.username=tester",
        "demo.shop.password=test-password",
        "demo.shop.token=test-token"
})
class ApiTestingEndToEndIntegrationTest {
    private static HttpServer httpServer;
    private static int httpPort;

    @Autowired TestPlatformService platformService;
    @Autowired TestCaseGenerationService generationService;
    @Autowired TestTaskService taskService;
    @Autowired TestExecutionRepository executionRepository;
    @Autowired TestCaseRepository caseRepository;
    @Autowired TestReportRepository reportRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired TestScheduleService scheduleService;

    @MockitoBean TestCaseGenerateAgent caseGenerateAgent;
    @MockitoBean AiTestEvaluatorAgent evaluatorAgent;
    @MockitoBean AiTestReportAgent reportAgent;
    @MockitoBean KnowledgeBaseService knowledgeBaseService;
    @MockitoBean(name = "myQwenChatModel") ChatModel qwenChatModel;
    @MockitoBean(name = "qwenStreamingChatModel") StreamingChatModel qwenStreamingChatModel;
    @MockitoBean(name = "qwenEmbeddingModel") EmbeddingModel qwenEmbeddingModel;
    @MockitoBean(name = "contentRetriever") ContentRetriever contentRetriever;

    @BeforeAll
    static void startFixtureServer() throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/health", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            int status;
            String body;
            if (query != null && query.contains("mode=invalid")) {
                status = 400;
                body = "{\"error\":\"invalid\"}";
            } else if (query != null && query.contains("mode=boundary")) {
                status = 200;
                body = "{\"count\":0}";
            } else {
                status = 200;
                body = "{\"status\":\"ok\"}";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        httpServer.start();
        httpPort = httpServer.getAddress().getPort();
    }

    @AfterAll
    static void stopFixtureServer() {
        if (httpServer != null) httpServer.stop(0);
    }

    @Test
    void migratesDatabaseAndRunsTheCompleteApiTestingWorkflowWithoutDuplicateCases() throws Exception {
        doNothing().when(knowledgeBaseService).indexApiDocument(anyString(), anyString());
        when(knowledgeBaseService.search(anyString(), eq(5))).thenReturn("已检索到接口测试原则和异常场景设计知识。");
        when(caseGenerateAgent.generate(anyString())).thenReturn(generatedCasesJson());
        when(evaluatorAgent.evaluate(anyString())).thenReturn("{\"pass\":true,\"score\":98,\"reason\":\"响应满足用例预期\",\"suggestion\":\"无\"}");
        when(reportAgent.summarize(anyString())).thenReturn("""
                {"overallConclusion":"本批次三个场景均符合接口契约，未发现失败。",
                 "riskLevel":"LOW",
                 "keyFindings":["正常、异常和边界场景均执行成功"],
                 "rootCauseAnalysis":["当前批次没有失败样本，无需归因"],
                 "recommendations":["持续补充真实业务数据和权限场景"],
                 "priorityActions":["将本批次纳入持续回归"],
                 "coverageObservations":["当前接口三类基础场景覆盖完整"]}
                """);

        int migrations = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = true", Integer.class);
        assertTrue(migrations >= 3, "Flyway V1/V2/V3 should be applied");
        assertTrue(columnExists("test_case", "fingerprint"), "duplicate fingerprint column should be migrated");
        assertTrue(columnExists("test_task", "request_json"), "async task request payload should be migrated");
        assertTrue(columnExists("test_schedule", "cron_expression"), "schedule table should be migrated");

        String openApi = """
                {
                  "openapi":"3.0.3",
                  "info":{"title":"E2E Demo API","version":"1.0.0"},
                  "servers":[{"url":"http://127.0.0.1:%d"}],
                  "paths":{
                    "/health":{
                      "get":{
                        "summary":"Health endpoint",
                        "responses":{
                          "200":{"description":"OK"},
                          "400":{"description":"Invalid input"}
                        }
                      }
                    }
                  }
                }
                """.formatted(httpPort);
        MockMultipartFile file = new MockMultipartFile("file", "e2e-openapi.json", "application/json",
                openApi.getBytes(StandardCharsets.UTF_8));
        ApiImportResult imported = platformService.upload(file);
        Long documentId = imported.document().getId();
        assertNotNull(documentId);
        assertEquals(1, imported.apiCount());

        var schedule = scheduleService.create(new CreateTestScheduleRequest(documentId, "E2E 每秒调度验收",
                "E2E Scheduler Task", "http://127.0.0.1:" + httpPort, "* * * * * *", true));
        assertNotNull(schedule.id());
        assertTrue(schedule.enabled());
        scheduleService.setEnabled(schedule.id(), false);
        assertFalse(scheduleService.list().stream().filter(s -> s.id().equals(schedule.id())).findFirst().orElseThrow().enabled());
        scheduleService.setEnabled(schedule.id(), true);

        List<TestCase> firstGeneration = generationService.generateForDocument(documentId);
        assertEquals(3, firstGeneration.size());
        assertTrue(generationService.coverageForDocument(documentId).complete());
        ScenarioCoverageResponse coverage = generationService.coverageForDocument(documentId);
        assertEquals(1, coverage.apiCount());
        assertEquals(3, coverage.totalCaseCount());
        assertEquals(1, coverage.apis().get(0).scenarioCounts().get("POSITIVE"));
        assertEquals(1, coverage.apis().get(0).scenarioCounts().get("NEGATIVE"));
        assertEquals(1, coverage.apis().get(0).scenarioCounts().get("BOUNDARY"));

        // Simulate a duplicate left by an older version: keep its history row but deactivate it.
        TestCase source = firstGeneration.get(0);
        TestCase legacyDuplicate = new TestCase();
        legacyDuplicate.setApiId(source.getApiId());
        legacyDuplicate.setCaseName(source.getCaseName());
        legacyDuplicate.setCaseType(source.getCaseType());
        legacyDuplicate.setPriority(source.getPriority());
        legacyDuplicate.setDescription(source.getDescription());
        legacyDuplicate.setRequestJson(source.getRequestJson());
        legacyDuplicate.setExpectResult(source.getExpectResult());
        legacyDuplicate.setAssertionsJson(source.getAssertionsJson());
        legacyDuplicate.setEnabled(true);
        Long duplicateId = caseRepository.save(legacyDuplicate).getId();

        // Second generation should reuse persisted cases, deactivate legacy duplicates and avoid another LLM call.
        List<TestCase> secondGeneration = generationService.generateForDocument(documentId);
        assertEquals(3, secondGeneration.size());
        assertFalse(caseRepository.findById(duplicateId).orElseThrow().getEnabled());
        assertEquals(3, jdbcTemplate.queryForObject("select count(distinct fingerprint) from test_case where fingerprint is not null", Integer.class));
        verify(caseGenerateAgent, times(1)).generate(anyString());

        TaskRunResponse run = taskService.createAndRun(new CreateTestTaskRequest(
                documentId, "E2E 自动化测试", "http://127.0.0.1:" + httpPort, null));

        assertEquals("COMPLETED", run.task().getStatus());
        assertEquals(3, run.executions().size());
        assertEquals(3, run.report().getTotalCount());
        assertEquals(3, run.report().getSuccessCount());
        assertEquals(0, run.report().getFailCount());
        assertEquals(0, run.report().getErrorCount());
        assertTrue(run.report().getAiAnalysis().contains("整批测试 AI 智能总结"));
        assertTrue(run.report().getAiAnalysis().contains("正常、异常和边界场景均执行成功"));
        assertEquals(3, executionRepository.findByTaskIdOrderByExecuteTimeAsc(run.task().getId()).size());
        assertTrue(reportRepository.findByTaskId(run.task().getId()).isPresent());
        verify(evaluatorAgent, times(3)).evaluate(anyString());
        verify(reportAgent, times(1)).summarize(contains("逐条执行证据"));

        // Verify the public asynchronous submission lifecycle against the same real local HTTP fixture.
        var accepted = taskService.submit(new CreateTestTaskRequest(
                documentId, "E2E 异步任务", "http://127.0.0.1:" + httpPort, null));
        assertNotNull(accepted.taskId());
        assertEquals("QUEUED", accepted.status());
        long deadline = System.currentTimeMillis() + 15000;
        String asyncStatus;
        do {
            asyncStatus = taskService.getProgress(accepted.taskId()).status();
            if ("COMPLETED".equals(asyncStatus) || "FAILED".equals(asyncStatus)) break;
            Thread.sleep(100);
        } while (System.currentTimeMillis() < deadline);
        assertEquals("COMPLETED", asyncStatus, "async task should finish through the background executor");
        assertEquals(3, taskService.executions(accepted.taskId()).size());
        assertTrue(taskService.reportByTask(accepted.taskId()).isPresent());

        // Manually tick the same scheduler service after the every-second Cron becomes due.
        Thread.sleep(1100);
        scheduleService.dispatchDueSchedules();
        Long scheduledTaskId = scheduleService.list().stream().filter(s -> s.id().equals(schedule.id()))
                .findFirst().orElseThrow().lastTaskId();
        assertNotNull(scheduledTaskId, "due Cron schedule should submit a background task");
        long scheduledDeadline = System.currentTimeMillis() + 15000;
        String scheduledStatus;
        do {
            scheduledStatus = taskService.getProgress(scheduledTaskId).status();
            if ("COMPLETED".equals(scheduledStatus) || "FAILED".equals(scheduledStatus)) break;
            Thread.sleep(100);
        } while (System.currentTimeMillis() < scheduledDeadline);
        assertEquals("COMPLETED", scheduledStatus, "scheduled task should execute through the async worker");
        scheduleService.delete(schedule.id());
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns where lower(table_name)=? and lower(column_name)=?",
                Integer.class, table, column);
        return count != null && count > 0;
    }

    private String generatedCasesJson() {
        return """
                {"cases":[
                  {"method":"GET","path":"/health","caseName":"正常请求","caseType":"POSITIVE","priority":"P1","description":"有效参数返回健康状态","request":{"pathParams":{},"queryParams":{"mode":"ok"},"headers":{},"body":null},"expected":{"statusCode":200,"assertions":[{"type":"JSON_PATH_EQUALS","expression":"$.status","expectedValue":"ok"}]}},
                  {"method":"GET","path":"/health","caseName":"异常参数请求","caseType":"NEGATIVE","priority":"P1","description":"非法参数应返回 400","request":{"pathParams":{},"queryParams":{"mode":"invalid"},"headers":{},"body":null},"expected":{"statusCode":400,"assertions":[{"type":"BODY_CONTAINS","expectedValue":"invalid"}]}},
                  {"method":"GET","path":"/health","caseName":"边界空值请求","caseType":"BOUNDARY","priority":"P2","description":"边界计数为零时返回有效响应","request":{"pathParams":{},"queryParams":{"mode":"boundary"},"headers":{},"body":null},"expected":{"statusCode":200,"assertions":[{"type":"JSON_PATH_EQUALS","expression":"$.count","expectedValue":0}]}}
                ]}
                """;
    }
}
