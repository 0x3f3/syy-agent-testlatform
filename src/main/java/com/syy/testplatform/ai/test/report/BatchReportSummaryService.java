package com.syy.testplatform.ai.test.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syy.testplatform.ai.agent.AiTestReportAgent;
import com.syy.testplatform.ai.test.entity.ApiDefinition;
import com.syy.testplatform.ai.test.entity.TestCase;
import com.syy.testplatform.ai.test.entity.TestExecution;
import com.syy.testplatform.ai.test.entity.TestTask;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class BatchReportSummaryService {
    private final AiTestReportAgent agent;
    private final ObjectMapper mapper;

    public BatchReportSummaryService(AiTestReportAgent agent, ObjectMapper mapper) {
        this.agent = agent;
        this.mapper = mapper;
    }

    /** Calls the report agent once with aggregate metrics and the batch's execution evidence. */
    public String summarize(TestTask task, List<TestExecution> executions, List<TestCase> cases,
                            Map<Long, ApiDefinition> apis, int judgeUnavailable) {
        try {
            String context = buildContext(task, executions, cases, apis, judgeUnavailable);
            String output = agent.summarize(context);
            JsonNode json = parse(output);
            return format(json, executions.size());
        } catch (Exception ex) {
            return fallback(executions, cases, apis, judgeUnavailable, ex);
        }
    }

    private String buildContext(TestTask task, List<TestExecution> executions, List<TestCase> cases,
                                Map<Long, ApiDefinition> apis, int judgeUnavailable) {
        Map<Long, TestCase> caseById = cases.stream().filter(c -> c.getId() != null)
                .collect(Collectors.toMap(TestCase::getId, c -> c, (a, b) -> a));
        long passed = executions.stream().filter(e -> "PASS".equals(e.getStatus())).count();
        long failed = executions.stream().filter(e -> "FAIL".equals(e.getStatus())).count();
        long errors = executions.stream().filter(e -> "ERROR".equals(e.getStatus())).count();
        double passRate = executions.isEmpty() ? 0 : passed * 100.0 / executions.size();
        double averageDuration = executions.stream().filter(e -> e.getDurationMs() != null)
                .mapToLong(TestExecution::getDurationMs).average().orElse(0);
        double averageScore = executions.stream().filter(e -> e.getScore() != null)
                .mapToInt(TestExecution::getScore).average().orElse(0);

        Map<String, long[]> endpointStats = new LinkedHashMap<>();
        for (TestExecution execution : executions) {
            ApiDefinition api = apis.get(execution.getApiId());
            String endpoint = api == null ? "API #" + execution.getApiId() : api.getMethod() + " " + api.getPath();
            long[] stats = endpointStats.computeIfAbsent(endpoint, ignored -> new long[4]);
            stats[0]++;
            if ("PASS".equals(execution.getStatus())) stats[1]++;
            else if ("FAIL".equals(execution.getStatus())) stats[2]++;
            else stats[3]++;
        }

        StringBuilder result = new StringBuilder();
        result.append("任务 ID: ").append(task.getId()).append('\n')
                .append("任务名称: ").append(task.getTaskName()).append('\n')
                .append("总用例数: ").append(executions.size()).append('\n')
                .append("PASS: ").append(passed).append("; FAIL: ").append(failed).append("; ERROR: ").append(errors).append('\n')
                .append("通过率: ").append(String.format(Locale.ROOT, "%.2f%%", passRate)).append('\n')
                .append("平均响应耗时: ").append(String.format(Locale.ROOT, "%.1f ms", averageDuration)).append('\n')
                .append("平均 Judge 评分: ").append(String.format(Locale.ROOT, "%.1f", averageScore)).append('\n')
                .append("Judge 不可用/跳过数: ").append(judgeUnavailable).append('\n')
                .append("接口维度统计（接口、总数、通过、失败、错误）:\n");
        endpointStats.forEach((endpoint, stats) -> result.append("- ").append(endpoint).append(": ")
                .append(stats[0]).append(", ").append(stats[1]).append(", ").append(stats[2]).append(", ").append(stats[3]).append('\n'));

        result.append("逐条执行证据（最多 120 条，含成功与失败样本）:\n");
        int limit = Math.min(executions.size(), 120);
        for (int i = 0; i < limit; i++) {
            TestExecution execution = executions.get(i);
            TestCase testCase = caseById.get(execution.getCaseId());
            ApiDefinition api = apis.get(execution.getApiId());
            result.append(i + 1).append(". ")
                    .append(api == null ? "API #" + execution.getApiId() : api.getMethod() + " " + api.getPath())
                    .append(" | 用例: ").append(testCase == null ? "#" + execution.getCaseId() : testCase.getCaseName())
                    .append(" | 场景: ").append(testCase == null ? "UNKNOWN" : testCase.getCaseType())
                    .append(" | 结果: ").append(execution.getStatus())
                    .append(" | HTTP: ").append(execution.getResponseStatus())
                    .append(" | 耗时: ").append(execution.getDurationMs()).append("ms")
                    .append(" | 评分: ").append(execution.getScore())
                    .append(" | Judge: ").append(Boolean.TRUE.equals(execution.getJudgeAvailable()) ? "available" : "unavailable")
                    .append(" | 响应摘要: ").append(limitText(execution.getActualResult(), 300))
                    .append(" | 评估依据: ").append(limitText(execution.getEvaluationReason(), 500))
                    .append(" | 建议: ").append(limitText(execution.getSuggestion(), 300)).append('\n');
        }
        if (executions.size() > limit) result.append("其余 ").append(executions.size() - limit).append(" 条执行记录未逐条展开；以上统计仍基于全量记录。\n");
        result.append("注意：执行记录中的请求/响应敏感字段已在入库前脱敏；不得补充输入中不存在的业务信息。");
        return result.toString();
    }

    private JsonNode parse(String output) throws Exception {
        if (output == null || output.isBlank()) throw new IllegalArgumentException("批量报告 Agent 返回为空");
        try { return mapper.readTree(output); } catch (Exception ignored) { }
        int start = output.indexOf('{');
        int end = output.lastIndexOf('}');
        if (start >= 0 && end > start) return mapper.readTree(output.substring(start, end + 1));
        throw new IllegalArgumentException("批量报告 Agent 未返回 JSON");
    }

    private String format(JsonNode json, int executionCount) {
        StringBuilder text = new StringBuilder();
        text.append("【整批测试 AI 智能总结】\n")
                .append("总体结论：").append(value(json, "overallConclusion", "Agent 未提供总体结论")).append('\n')
                .append("风险等级：").append(normalizeRisk(value(json, "riskLevel", "MEDIUM"))).append('\n')
                .append("分析样本：").append(executionCount).append(" 条执行记录\n");
        appendList(text, "主要发现", json.path("keyFindings"));
        appendList(text, "根因分析（含待验证推测）", json.path("rootCauseAnalysis"));
        appendList(text, "覆盖情况观察", json.path("coverageObservations"));
        appendList(text, "改进建议", json.path("recommendations"));
        appendList(text, "优先处理动作", json.path("priorityActions"));
        text.append("\n说明：该总结由 LLM 基于本批次全量统计及最多 120 条逐条执行证据生成；结论仍需结合目标系统业务规则复核。");
        return text.toString();
    }

    private void appendList(StringBuilder text, String title, JsonNode values) {
        text.append(title).append("：\n");
        if (!values.isArray() || values.isEmpty()) {
            text.append("- 无明确发现\n");
            return;
        }
        int index = 0;
        for (JsonNode value : values) {
            if (index++ >= 5) break;
            text.append("- ").append(value.asText()).append('\n');
        }
    }

    private String fallback(List<TestExecution> executions, List<TestCase> cases, Map<Long, ApiDefinition> apis,
                            int judgeUnavailable, Exception error) {
        long passed = executions.stream().filter(e -> "PASS".equals(e.getStatus())).count();
        long failed = executions.stream().filter(e -> "FAIL".equals(e.getStatus())).count();
        long errors = executions.stream().filter(e -> "ERROR".equals(e.getStatus())).count();
        StringBuilder text = new StringBuilder("【整批测试规则摘要（AI 总结不可用）】\n")
                .append("AI 总结调用失败：").append(limitText(sanitizeError(error.getMessage()), 300)).append('\n')
                .append("执行总数：").append(executions.size()).append("；PASS：").append(passed)
                .append("；FAIL：").append(failed).append("；ERROR：").append(errors).append('\n')
                .append("通过率：").append(executions.isEmpty() ? "0.00%" : String.format(Locale.ROOT, "%.2f%%", passed * 100.0 / executions.size())).append('\n')
                .append("Judge 不可用/跳过：").append(judgeUnavailable).append(" 条\n")
                .append("主要失败项：\n");
        executions.stream().filter(e -> !"PASS".equals(e.getStatus())).limit(10).forEach(e -> {
            ApiDefinition api = apis.get(e.getApiId());
            text.append("- ").append(api == null ? "API #" + e.getApiId() : api.getMethod() + " " + api.getPath())
                    .append(" | ").append(e.getStatus()).append(" | HTTP ").append(e.getResponseStatus())
                    .append(" | ").append(limitText(e.getEvaluationReason(), 300)).append('\n');
        });
        if (failed == 0 && errors == 0) text.append("- 本批次未记录 FAIL/ERROR；仍需结合业务断言和场景覆盖确认质量。\n");
        text.append("改进建议：检查失败接口的评估原因；确认 Judge 配置和场景覆盖；对高频失败接口补充回归用例。");
        return text.toString();
    }

    private String value(JsonNode json, String key, String fallback) {
        String value = json.path(key).asText("").trim();
        return value.isBlank() ? fallback : value;
    }
    private String normalizeRisk(String value) {
        String risk = value.toUpperCase(Locale.ROOT);
        return Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(risk) ? risk : "MEDIUM（模型输出未识别，按中风险展示）";
    }
    private String sanitizeError(String value) {
        if (value == null) return "";
        return value.replaceAll("(?i)(token|access_token|api[_-]?key|secret|password)(\\s*[:=]\\s*)([^&\\s,]+)", "$1$2***");
    }
    private String limitText(String value, int max) {
        if (value == null) return "";
        return value.length() > max ? value.substring(0, max) + "...(truncated)" : value;
    }
}
