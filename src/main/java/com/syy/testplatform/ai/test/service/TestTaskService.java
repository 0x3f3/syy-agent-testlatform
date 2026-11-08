package com.syy.testplatform.ai.test.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.syy.testplatform.ai.test.entity.*;
import com.syy.testplatform.ai.test.evaluation.AssertionEvaluator;
import com.syy.testplatform.ai.test.evaluation.LlmEvaluationService;
import com.syy.testplatform.ai.test.report.BatchReportSummaryService;
import com.syy.testplatform.ai.test.repository.*;
import com.syy.testplatform.test.executor.HttpExecutionResult;
import com.syy.testplatform.test.executor.HttpExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;

@Service
public class TestTaskService {
    private static final Logger log = LoggerFactory.getLogger(TestTaskService.class);

    private final AgentTestTaskRepository taskRepository;
    private final TestExecutionRepository executionRepository;
    private final TestReportRepository reportRepository;
    private final ApiDocumentRepository documentRepository;
    private final ApiDefinitionRepository apiRepository;
    private final TestCaseRepository caseRepository;
    private final TestCaseGenerationService generationService;
    private final HttpExecutor executor;
    private final AssertionEvaluator assertionEvaluator;
    private final LlmEvaluationService llmJudge;
    private final BatchReportSummaryService batchSummary;
    private final ObjectMapper mapper;
    private final AsyncTestTaskRunner asyncRunner;
    private final RedisPlatformCache cache;
    private final Executor httpCaseExecutor;
    private final BoundedConcurrentExecutor concurrentExecutor = new BoundedConcurrentExecutor();
    private final int perTaskConcurrency;

    public TestTaskService(AgentTestTaskRepository taskRepository,
                           TestExecutionRepository executionRepository,
                           TestReportRepository reportRepository,
                           ApiDocumentRepository documentRepository,
                           ApiDefinitionRepository apiRepository,
                           TestCaseRepository caseRepository,
                           TestCaseGenerationService generationService,
                           HttpExecutor executor,
                           AssertionEvaluator assertionEvaluator,
                           LlmEvaluationService llmJudge,
                           BatchReportSummaryService batchSummary,
                           ObjectMapper mapper,
                           @Lazy AsyncTestTaskRunner asyncRunner,
                           RedisPlatformCache cache,
                           @Qualifier("httpCaseExecutor") Executor httpCaseExecutor,
                           @Value("${app.execution.per-task-concurrency:4}") int perTaskConcurrency) {
        this.taskRepository = taskRepository;
        this.executionRepository = executionRepository;
        this.reportRepository = reportRepository;
        this.documentRepository = documentRepository;
        this.apiRepository = apiRepository;
        this.caseRepository = caseRepository;
        this.generationService = generationService;
        this.executor = executor;
        this.assertionEvaluator = assertionEvaluator;
        this.llmJudge = llmJudge;
        this.batchSummary = batchSummary;
        this.mapper = mapper;
        this.asyncRunner = asyncRunner;
        this.cache = cache;
        this.httpCaseExecutor = httpCaseExecutor;
        this.perTaskConcurrency = Math.max(1, perTaskConcurrency);
    }

    /** Creates a durable QUEUED row and returns immediately; execution is delegated to the async pool. */
    public TaskSubmissionResponse submit(CreateTestTaskRequest request) {
        ApiDocument document = validateRequest(request);
        TestTask task = new TestTask();
        task.setDocumentId(document.getId());
        task.setTaskName(limit(blankTo(request.taskName(), document.getTitle() + " 自动化测试"), 250));
        task.setBaseUrl(limit(blankTo(request.baseUrl(), document.getServerUrl()), 1000));
        task.setStatus("QUEUED");
        task.setTotalCount(0);
        task.setPassedCount(0);
        task.setFailedCount(0);
        task.setErrorCount(0);
        task.setCreatedAt(LocalDateTime.now());
        try { task.setRequestJson(mapper.writeValueAsString(request)); }
        catch (Exception ex) { throw new IllegalArgumentException("测试任务参数序列化失败", ex); }
        task = taskRepository.save(task);
        refreshProgress(task);
        try {
            asyncRunner.run(task.getId());
        } catch (RuntimeException rejected) {
            markTaskFailed(task, "异步任务队列已满，请稍后重试：" + safeMessage(rejected));
            throw new IllegalStateException("当前任务队列繁忙，请稍后重试", rejected);
        }
        return new TaskSubmissionResponse(task.getId(), task.getStatus(), "任务已提交，可通过 taskId 查询执行进度");
    }

    /** Worker entry point. Errors are persisted so the polling client always sees a terminal state. */
    public void executeSubmittedTask(Long taskId) {
        TestTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null || !"QUEUED".equals(task.getStatus())) return;
        try {
            CreateTestTaskRequest request = mapper.readValue(task.getRequestJson(), CreateTestTaskRequest.class);
            task.setStatus("RUNNING");
            task.setStartedAt(LocalDateTime.now());
            taskRepository.save(task);
            refreshProgress(task);
            runExistingTask(task, request);
        } catch (Exception ex) {
            log.error("测试任务运行失败，taskId={}", taskId, ex);
            markTaskFailed(task, safeMessage(ex));
        }
    }

    /** Kept for internal callers and deterministic integration tests; the public REST endpoint is async. */
    public TaskRunResponse createAndRun(CreateTestTaskRequest request) {
        ApiDocument document = validateRequest(request);
        TestTask task = new TestTask();
        task.setDocumentId(document.getId());
        task.setTaskName(limit(blankTo(request.taskName(), document.getTitle() + " 自动化测试"), 250));
        task.setBaseUrl(limit(blankTo(request.baseUrl(), document.getServerUrl()), 1000));
        task.setStatus("RUNNING");
        task.setStartedAt(LocalDateTime.now());
        task.setCreatedAt(LocalDateTime.now());
        try { task.setRequestJson(mapper.writeValueAsString(request)); }
        catch (Exception ex) { throw new IllegalArgumentException("测试任务参数序列化失败", ex); }
        task = taskRepository.save(task);
        refreshProgress(task);
        try { return runExistingTask(task, request); }
        catch (Exception ex) {
            markTaskFailed(task, safeMessage(ex));
            throw ex instanceof RuntimeException runtime ? runtime : new IllegalStateException(ex);
        }
    }

    private ApiDocument validateRequest(CreateTestTaskRequest request) {
        if (request == null || request.documentId() == null) throw new IllegalArgumentException("documentId 不能为空");
        ApiDocument document = documentRepository.findById(request.documentId())
                .orElseThrow(() -> new IllegalArgumentException("OpenAPI 文档不存在"));
        if (apiRepository.findByDocumentIdOrderByIdAsc(document.getId()).isEmpty())
            throw new IllegalStateException("该文档没有可执行接口");
        return document;
    }

    private TaskRunResponse runExistingTask(TestTask task, CreateTestTaskRequest request) {
        ApiDocument document = documentRepository.findById(task.getDocumentId())
                .orElseThrow(() -> new IllegalArgumentException("OpenAPI 文档不存在"));
        List<ApiDefinition> apis = apiRepository.findByDocumentIdOrderByIdAsc(document.getId());
        if (apis.isEmpty()) throw new IllegalStateException("该文档没有可执行接口");
        List<Long> apiIds = apis.stream().map(ApiDefinition::getId).toList();
        List<TestCase> cases;
        if (request.caseIds() != null && !request.caseIds().isEmpty()) {
            cases = caseRepository.findAllById(request.caseIds());
            if (cases.size() != request.caseIds().size()) throw new IllegalArgumentException("部分测试用例不存在");
            if (cases.stream().anyMatch(c -> !apiIds.contains(c.getApiId())))
                throw new IllegalArgumentException("所选测试用例不属于当前 OpenAPI 文档");
            if (cases.stream().anyMatch(c -> Boolean.FALSE.equals(c.getEnabled())))
                throw new IllegalArgumentException("所选测试用例已停用（可能是重复用例），请重新选择启用用例");
        } else {
            cases = generationService.generateForDocument(document.getId());
        }
        if (cases.isEmpty()) throw new IllegalStateException("没有可执行测试用例，请先生成测试用例");
        if (cases.size() > 200) throw new IllegalArgumentException("单次任务最多执行 200 条用例，请分批执行");

        Map<Long, ApiDefinition> apiMap = new HashMap<>();
        apis.forEach(api -> apiMap.put(api.getId(), api));
        task.setTotalCount(cases.size());
        task.setPassedCount(0);
        task.setFailedCount(0);
        task.setErrorCount(0);
        task.setStatus("RUNNING");
        if (task.getStartedAt() == null) task.setStartedAt(LocalDateTime.now());
        taskRepository.save(task);
        refreshProgress(task);

        List<BoundedConcurrentExecutor.Outcome<TestCase, TestExecution>> outcomes = concurrentExecutor.execute(
                cases, perTaskConcurrency, httpCaseExecutor,
                testCase -> executeOne(task, testCase, apiMap.get(testCase.getApiId()), request));

        List<TestExecution> executions = new ArrayList<>(outcomes.size());
        int passed = 0, failed = 0, errors = 0, judgeUnavailable = 0;
        for (BoundedConcurrentExecutor.Outcome<TestCase, TestExecution> outcome : outcomes) {
            TestExecution execution = outcome.succeeded()
                    ? outcome.result()
                    : errorExecution(task, outcome.item(), apiMap.get(outcome.item() == null ? null : outcome.item().getApiId()), outcome.error());
            if (execution == null) execution = errorExecution(task, outcome.item(), null, new IllegalStateException("用例执行没有产生结果"));
            execution.setTaskId(task.getId());
            execution = executionRepository.save(execution);
            executions.add(execution);
            if ("PASS".equals(execution.getStatus())) passed++;
            else if ("FAIL".equals(execution.getStatus())) failed++;
            else errors++;
            if (!Boolean.TRUE.equals(execution.getJudgeAvailable())) judgeUnavailable++;

            task.setPassedCount(passed);
            task.setFailedCount(failed);
            task.setErrorCount(errors);
            taskRepository.save(task);
            refreshProgress(task);
        }

        task.setTotalCount(executions.size());
        task.setPassedCount(passed);
        task.setFailedCount(failed);
        task.setErrorCount(errors);
        task.setStatus("COMPLETED");
        task.setFinishedAt(LocalDateTime.now());
        taskRepository.save(task);

        TestReport report = new TestReport();
        report.setTaskId(task.getId());
        report.setTotalCount(executions.size());
        report.setSuccessCount(passed);
        report.setFailCount(failed);
        report.setErrorCount(errors);
        report.setPassRate(executions.isEmpty() ? 0.0 : round(passed * 100.0 / executions.size()));
        report.setAverageScore(executions.isEmpty() ? 0.0 : round(executions.stream()
                .mapToInt(e -> e.getScore() == null ? 0 : e.getScore()).average().orElse(0)));
        report.setAiAnalysis(batchSummary.summarize(task, executions, cases, apiMap, judgeUnavailable));
        report = reportRepository.save(report);
        refreshProgress(task);
        return new TaskRunResponse(task, executions, report);
    }

    private TestExecution executeOne(TestTask task, TestCase testCase, ApiDefinition api, CreateTestTaskRequest request) {
        if (api == null) throw new IllegalStateException("测试用例关联的接口定义不存在");
        String requestBase = request.baseUrl();
        String targetBase = requestBase != null && !requestBase.isBlank() ? requestBase
                : (api.getServerUrl() != null && !api.getServerUrl().isBlank() ? api.getServerUrl() : task.getBaseUrl());
        HttpExecutionResult actual = executor.execute(api, targetBase, testCase.getRequestJson());
        AssertionEvaluator.DeterministicResult deterministic = assertionEvaluator.evaluate(testCase.getExpectResult(), actual);
        String actualForJudge = "HTTP " + actual.statusCode() + "\n响应头：" + redactHeaderMap(actual.headers())
                + "\n响应体：" + redactJsonString(actual.body()) + "\n请求错误：" + actual.transportError();
        LlmEvaluationService.JudgeResult judge = actual.transportError() != null
                ? new LlmEvaluationService.JudgeResult(false, 0, "HTTP 请求未获得响应，跳过 LLM Judge",
                    "检查目标服务地址、网络和接口可用性", false, "")
                : llmJudge.evaluate(testCase.getCaseName(), testCase.getExpectResult(), actualForJudge, deterministic);
        String status = actual.transportError() != null ? "ERROR"
                : (!deterministic.pass() ? "FAIL" : (!judge.available() ? "ERROR" : (judge.pass() ? "PASS" : "FAIL")));

        TestExecution execution = new TestExecution();
        execution.setCaseId(testCase.getId());
        execution.setApiId(api.getId());
        execution.setRequestUrl(limit(maskUrl(actual.url()), 1000));
        execution.setRequestMethod(actual.method());
        execution.setRequestHeadersJson(redactHeadersFromRequest(testCase.getRequestJson()));
        execution.setRequestBody(redactJsonString(extractBody(testCase.getRequestJson())));
        execution.setResponseStatus(actual.statusCode() == 0 ? null : actual.statusCode());
        execution.setResponseHeadersJson(redactHeaderMap(actual.headers()));
        execution.setActualResult(redactJsonString(actual.body()));
        execution.setDurationMs(actual.durationMs());
        execution.setStatus(status);
        execution.setScore(judge.available() ? judge.score() : 0);
        execution.setJudgeAvailable(judge.available());
        String reason = String.join("；", deterministic.failures());
        if (!judge.reason().isBlank()) reason += (reason.isBlank() ? "" : "；") + judge.reason();
        execution.setEvaluationReason(limit(reason, 1900));
        execution.setSuggestion(limit(judge.suggestion(), 1900));
        execution.setExecuteTime(LocalDateTime.now());
        return execution;
    }

    private TestExecution errorExecution(TestTask task, TestCase testCase, ApiDefinition api, Throwable error) {
        TestExecution execution = new TestExecution();
        execution.setTaskId(task.getId());
        execution.setCaseId(testCase == null ? null : testCase.getId());
        execution.setApiId(api == null ? (testCase == null ? null : testCase.getApiId()) : api.getId());
        execution.setRequestMethod(api == null ? null : api.getMethod());
        execution.setRequestUrl(api == null ? null : limit(maskUrl((api.getServerUrl() == null ? "" : api.getServerUrl()) + api.getPath()), 1000));
        execution.setStatus("ERROR");
        execution.setScore(0);
        execution.setJudgeAvailable(false);
        execution.setEvaluationReason(limit("用例执行异常：" + safeMessage(error), 1900));
        execution.setSuggestion("检查接口定义、请求参数、线程池状态和目标服务可用性");
        execution.setActualResult("");
        execution.setExecuteTime(LocalDateTime.now());
        return execution;
    }

    public List<TestTask> listTasks() { return taskRepository.findAllByOrderByCreatedAtDesc(); }

    public TestTask getTask(Long id) {
        return taskRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("测试任务不存在：" + id));
    }

    public TaskProgressResponse getProgress(Long id) {
        Optional<TaskProgressResponse> cached = cache.getTaskProgress(id);
        if (cached.isPresent() && cached.get().updatedAt() != null
                && cached.get().updatedAt().isAfter(LocalDateTime.now().minusSeconds(30))) return cached.get();
        TestTask task = getTask(id);
        TaskProgressResponse progress = toProgress(task);
        cache.putTaskProgress(progress);
        return progress;
    }

    public List<TestExecution> executions(Long taskId) { getTask(taskId); return executionRepository.findByTaskIdOrderByExecuteTimeAsc(taskId); }
    public Optional<TestReport> reportByTask(Long taskId) { return reportRepository.findByTaskId(taskId); }
    public List<TestReport> reports() { return reportRepository.findAllByOrderByCreateTimeAsc(); }
    public List<TestExecution> allExecutions() { return executionRepository.findAllByOrderByExecuteTimeDesc(); }

    private void markTaskFailed(TestTask task, String message) {
        if (task == null) return;
        task.setStatus("FAILED");
        task.setErrorMessage(limit(message, 1900));
        task.setFinishedAt(LocalDateTime.now());
        taskRepository.save(task);
        refreshProgress(task);
    }

    private void refreshProgress(TestTask task) {
        cache.putTaskProgress(toProgress(task));
    }

    private TaskProgressResponse toProgress(TestTask task) {
        int total = safeInt(task.getTotalCount());
        int passed = safeInt(task.getPassedCount());
        int failed = safeInt(task.getFailedCount());
        int errors = safeInt(task.getErrorCount());
        int completed = passed + failed + errors;
        int percent = total == 0 ? ("COMPLETED".equals(task.getStatus()) ? 100 : 0)
                : Math.min(100, (int) Math.floor(completed * 100.0 / total));
        return new TaskProgressResponse(task.getId(), task.getStatus(), total, completed, passed, failed, errors,
                percent, task.getCreatedAt(), task.getStartedAt(), task.getFinishedAt(), LocalDateTime.now(),
                task.getErrorMessage(), "COMPLETED".equals(task.getStatus()) && reportRepository.findByTaskId(task.getId()).isPresent());
    }

    private int safeInt(Integer value) { return value == null ? 0 : value; }
    private String blankTo(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private double round(double value) { return Math.round(value * 100.0) / 100.0; }
    private String limit(String value, int max) { return value == null ? null : value.substring(0, Math.min(value.length(), max)); }
    private String safeMessage(Throwable ex) {
        if (ex == null) return "未知错误";
        String message = ex.getMessage();
        if (message == null || message.isBlank()) message = ex.getClass().getSimpleName();
        return message.replaceAll("(?i)(token|access_token|api[_-]?key|secret|password)(\\s*[:=]\\s*)([^&\\s,]+)", "$1$2***");
    }

    private String extractBody(String request) {
        try { JsonNode node = mapper.readTree(request); JsonNode body = node.path("body"); return body.isMissingNode() || body.isNull() ? "" : mapper.writeValueAsString(body); }
        catch (Exception e) { return ""; }
    }
    private String redactHeadersFromRequest(String request) {
        try { JsonNode node = mapper.readTree(request).path("headers"); return redactJson(mapper.writeValueAsString(node)); }
        catch (Exception e) { return "{}"; }
    }
    private String redactHeaderMap(Map<String, List<String>> headers) {
        try { ObjectNode safe = mapper.createObjectNode(); headers.forEach((k, v) -> safe.put(k, isSensitive(k) ? "***" : String.join(",", v))); return mapper.writeValueAsString(safe); }
        catch (Exception e) { return "{}"; }
    }
    private String redactJsonString(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        try { return redactJson(raw); }
        catch (Exception e) { return raw.replaceAll("(?i)(password|token|secret|authorization|api[_-]?key)(\\s*[:=]\\s*)([^,\\s&]+)", "$1$2***"); }
    }
    private String redactJson(String raw) throws Exception { JsonNode node = mapper.readTree(raw); redactNode(node); return mapper.writeValueAsString(node); }
    private void redactNode(JsonNode node) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) { var e = it.next(); if (isSensitive(e.getKey())) ((ObjectNode) node).put(e.getKey(), "***"); else redactNode(e.getValue()); }
        } else if (node.isArray()) node.forEach(this::redactNode);
    }
    private boolean isSensitive(String key) {
        return key != null && key.toLowerCase(Locale.ROOT)
                .matches(".*(password|token|secret|authorization|api[_-]?key|credential).*");
    }
    private String maskUrl(String url) {
        if (url == null) return "";
        return url.replaceAll("(?i)([?&](?:token|access_token|api_key|password|secret)=)[^&]+", "$1***");
    }
}
