package com.syy.testplatform.ai.test.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.syy.testplatform.ai.test.entity.ApiDefinition;
import com.syy.testplatform.ai.test.entity.TestCase;
import com.syy.testplatform.ai.test.repository.ApiDefinitionRepository;
import com.syy.testplatform.ai.test.repository.TestCaseRepository;
import com.syy.testplatform.ai.test.rag.KnowledgeBaseService;
import com.syy.testplatform.ai.test.rag.RagSearchDiagnostics;
import com.syy.testplatform.test.agent.TestCaseGenerateAgent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public class TestCaseGenerationService {
    private final TestCaseGenerateAgent agent;
    private final ApiDefinitionRepository apiRepository;
    private final TestCaseRepository caseRepository;
    private final KnowledgeBaseService knowledgeBase;
    private final ObjectMapper mapper;

    public TestCaseGenerationService(TestCaseGenerateAgent agent,
                                     ApiDefinitionRepository apiRepository,
                                     TestCaseRepository caseRepository,
                                     KnowledgeBaseService knowledgeBase,
                                     ObjectMapper mapper) {
        this.agent = agent;
        this.apiRepository = apiRepository;
        this.caseRepository = caseRepository;
        this.knowledgeBase = knowledgeBase;
        this.mapper = mapper;
    }

    /**
     * Generates only missing scenario categories. Existing cases are reused and fingerprinted,
     * so clicking generate repeatedly will not create duplicate test cases.
     */
    @Transactional
    public List<TestCase> generateForDocument(Long documentId) {
        List<ApiDefinition> apis = apiRepository.lockByDocumentId(documentId);
        if (apis.isEmpty()) throw new IllegalArgumentException("该文档尚未解析出接口");

        List<Long> apiIds = apis.stream().map(ApiDefinition::getId).toList();
        List<TestCase> existing = caseRepository.findByApiIdInOrderByIdAsc(apiIds);
        Set<String> knownFingerprints = new HashSet<>();
        List<TestCase> activeExisting = new ArrayList<>();
        List<TestCase> fingerprintBackfill = new ArrayList<>();
        List<TestCase> legacyDuplicates = new ArrayList<>();
        for (TestCase oldCase : existing) {
            if (Boolean.FALSE.equals(oldCase.getEnabled())) continue;
            String fingerprint = hasText(oldCase.getFingerprint())
                    ? oldCase.getFingerprint() : fingerprint(oldCase.getApiId(), oldCase.getRequestJson(), oldCase.getExpectResult());
            if (knownFingerprints.add(fingerprint)) {
                activeExisting.add(oldCase);
                if (!hasText(oldCase.getFingerprint())) {
                    oldCase.setFingerprint(fingerprint);
                    fingerprintBackfill.add(oldCase);
                }
            } else {
                // Keep historical rows for existing execution references, but do not run duplicates again.
                oldCase.setEnabled(false);
                legacyDuplicates.add(oldCase);
            }
        }
        if (!fingerprintBackfill.isEmpty()) caseRepository.saveAll(fingerprintBackfill);
        if (!legacyDuplicates.isEmpty()) caseRepository.saveAll(legacyDuplicates);

        Map<Long, ApiDefinition> apiById = new HashMap<>();
        apis.forEach(api -> apiById.put(api.getId(), api));
        Map<Long, EnumSet<Scenario>> initialCoverage = coverage(activeExisting);

        List<ApiDefinition> uncoveredApis = apis.stream()
                .filter(api -> !missingScenarios(
                        initialCoverage.get(api.getId())
                ).isEmpty())
                .toList();

        Map<Long, EnumSet<Scenario>> coverage = initialCoverage;

        if (uncoveredApis.isEmpty()) return activeExisting;

        String knowledge = knowledgeBase.search(buildRagQuery(uncoveredApis), 5);
        List<TestCase> generated = new ArrayList<>();
        Set<String> batchFingerprints = new HashSet<>(knownFingerprints);
        String prompt = buildPrompt(uncoveredApis, coverage, knowledge, null);

        for (int attempt = 0; attempt < 2; attempt++) {
            String output = agent.generate(prompt);
            JsonNode root = parseJson(output);
            JsonNode cases = root.isArray() ? root : root.path("cases");
            if (!cases.isArray() || cases.isEmpty()) {
                throw new IllegalStateException("Agent 未返回有效的 cases JSON 数组：" + shortText(output));
            }

            int count = 0;
            for (JsonNode item : cases) {
                if (count++ >= 200) break;
                ApiDefinition api = resolveApi(item, apiById, uncoveredApis);
                if (api == null) continue;

                TestCase testCase = toTestCase(item, api);
                if (testCase == null) continue;
                String fingerprint = fingerprint(api.getId(), testCase.getRequestJson(), testCase.getExpectResult());
                if (!batchFingerprints.add(fingerprint)) continue;
                testCase.setFingerprint(fingerprint);
                generated.add(testCase);
                if (existing.size() + generated.size() > 200) {
                    throw new IllegalStateException("当前文档累计测试用例超过 200 条，请拆分接口文档或分批管理用例");
                }
            }

            List<TestCase> merged = new ArrayList<>(activeExisting);
            merged.addAll(generated);
            coverage = coverage(merged);
            List<String> gaps = coverageGaps(apis, coverage);
            if (gaps.isEmpty()) {
                if (!generated.isEmpty()) caseRepository.saveAll(generated);
                return caseRepository.findByApiIdInAndEnabledTrueOrderByIdAsc(apiIds);
            }
            if (attempt == 0) prompt = buildPrompt(uncoveredApis, coverage, knowledge, gaps);
            else throw new IllegalStateException("测试场景覆盖校验未通过，以下接口仍缺少场景：" + String.join("；", gaps));
        }
        throw new IllegalStateException("测试用例生成失败，请检查 Agent 输出");
    }

    public RagSearchDiagnostics previewRagContextForDocument(Long documentId) {
        List<ApiDefinition> apis = apiRepository.findByDocumentIdOrderByIdAsc(documentId);
        if (apis.isEmpty()) throw new IllegalArgumentException("该文档尚未解析出接口");
        return knowledgeBase.searchDebug(buildRagQuery(apis), 5);
    }

    @Transactional(readOnly = true)
    public ScenarioCoverageResponse coverageForDocument(Long documentId) {
        List<ApiDefinition> apis = apiRepository.findByDocumentIdOrderByIdAsc(documentId);
        if (apis.isEmpty()) throw new IllegalArgumentException("该文档尚未解析出接口");
        List<Long> ids = apis.stream().map(ApiDefinition::getId).toList();
        List<TestCase> cases = caseRepository.findByApiIdInAndEnabledTrueOrderByIdAsc(ids);
        Map<Long, EnumSet<Scenario>> coverage = coverage(cases);
        List<ScenarioCoverageResponse.ApiCoverage> rows = new ArrayList<>();
        for (ApiDefinition api : apis) {
            List<TestCase> apiCases = cases.stream().filter(c -> Objects.equals(c.getApiId(), api.getId())).toList();
            EnumSet<Scenario> present = coverage.getOrDefault(api.getId(), EnumSet.noneOf(Scenario.class));
            List<String> missing = missingScenarios(present).stream().map(Enum::name).toList();
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (Scenario scenario : Scenario.values()) {
                counts.put(scenario.name(), (int) apiCases.stream().filter(c -> classify(c.getCaseType(), c.getCaseName(), c.getDescription()) == scenario).count());
            }
            rows.add(new ScenarioCoverageResponse.ApiCoverage(api.getId(), api.getMethod() + " " + api.getPath(),
                    apiCases.size(), counts, missing, missing.isEmpty()));
        }
        boolean complete = rows.stream().allMatch(ScenarioCoverageResponse.ApiCoverage::complete);
        return new ScenarioCoverageResponse(documentId, apis.size(), cases.size(), complete, rows);
    }

    private String buildPrompt(List<ApiDefinition> apis, Map<Long, EnumSet<Scenario>> coverage, String knowledge, List<String> gaps) {
        StringBuilder apiText = new StringBuilder();
        for (ApiDefinition api : apis) {
            List<String> missing = missingScenarios(coverage.get(api.getId())).stream().map(Enum::name).toList();
            apiText.append("\n接口 ID: ").append(api.getId())
                    .append("\n方法: ").append(api.getMethod())
                    .append("\n路径: ").append(api.getPath())
                    .append("\n描述: ").append(api.getDescription())
                    .append("\n参数: ").append(safe(api.getParametersJson()))
                    .append("\n请求 Schema: ").append(safe(api.getRequestBodySchemaJson()))
                    .append("\n请求示例: ").append(safe(api.getRequestExampleJson()))
                    .append("\n响应: ").append(safe(api.getResponsesJson()))
                    .append("\n安全配置: ").append(safe(api.getSecurityJson()))
                    .append("\n必须补齐的场景: ").append(missing)
                    .append("\n");
        }
        StringBuilder prompt = new StringBuilder("以下是需要补齐测试场景的接口定义：\n").append(apiText)
                .append("\n相关测试知识（RAG 检索结果）：\n").append(knowledge)
                .append("\n要求：每个接口都必须至少有一条 POSITIVE 正常场景、一条 NEGATIVE 异常/非法/缺失参数场景、一条 BOUNDARY 边界/临界值场景。")
                .append("只生成当前缺失的场景类别，不能用重复请求冒充不同场景。用例必须可执行，字段和状态码只能依据契约或明确规则，不得编造。")
                .append("caseType 只能使用 POSITIVE、NEGATIVE、BOUNDARY。只输出严格 JSON，格式为 {\"cases\":[{\"apiId\":1,\"method\":\"GET\",\"path\":\"/path\",\"caseName\":\"...\",\"caseType\":\"POSITIVE\",\"priority\":\"P1\",\"description\":\"...\",\"request\":{\"pathParams\":{},\"queryParams\":{},\"headers\":{},\"body\":null},\"expected\":{\"statusCode\":200,\"assertions\":[]}}]}。");
        if (gaps != null && !gaps.isEmpty()) prompt.append("\n上一次生成后仍未覆盖：").append(String.join("；", gaps)).append("。本次必须补齐这些缺失类别。");
        return prompt.toString();
    }

    private String buildRagQuery(List<ApiDefinition> apis) {
        StringBuilder query = new StringBuilder("API 自动化测试设计，重点覆盖正常、异常、边界、参数校验、认证和响应断言。\n");
        apis.forEach(api -> query.append(api.getMethod()).append(' ').append(api.getPath()).append(' ').append(api.getDescription()).append('\n'));
        return query.toString();
    }

    private ApiDefinition resolveApi(JsonNode item, Map<Long, ApiDefinition> byId, List<ApiDefinition> allowed) {
        long apiId = item.path("apiId").asLong(-1);
        ApiDefinition api = apiId > 0 ? byId.get(apiId) : null;
        if (api == null) {
            String method = item.path("method").asText("").toUpperCase(Locale.ROOT);
            String path = item.path("path").asText("");
            api = allowed.stream().filter(a -> a.getMethod().equalsIgnoreCase(method) && a.getPath().equals(path)).findFirst().orElse(null);
        }
        if (api == null) return null;
        Long resolvedId = api.getId();
        return allowed.stream().anyMatch(a -> Objects.equals(a.getId(), resolvedId)) ? api : null;
    }

    private TestCase toTestCase(JsonNode item, ApiDefinition api) {
        String caseName = limit(item.path("caseName").asText("Agent 测试用例"), 250);
        String rawType = item.path("caseType").asText("");
        Scenario scenario = classify(rawType, caseName, item.path("description").asText(""));
        if (scenario == null) return null;

        TestCase testCase = new TestCase();
        testCase.setApiId(api.getId());
        testCase.setCaseName(caseName);
        testCase.setCaseType(scenario.name());
        testCase.setPriority(limit(item.path("priority").asText("P2"), 10));
        testCase.setDescription(limit(item.path("description").asText(""), 950));

        JsonNode request = item.path("request");
        if (!request.isObject()) request = mapper.createObjectNode();
        ObjectNode normalizedRequest = (ObjectNode) request.deepCopy();
        if (!normalizedRequest.path("pathParams").isObject()) normalizedRequest.set("pathParams", mapper.createObjectNode());
        if (!normalizedRequest.path("queryParams").isObject()) normalizedRequest.set("queryParams", mapper.createObjectNode());
        if (!normalizedRequest.path("headers").isObject()) normalizedRequest.set("headers", mapper.createObjectNode());
        if (!normalizedRequest.has("body")) normalizedRequest.putNull("body");

        JsonNode expected = item.path("expected");
        if (!expected.isObject()) expected = mapper.createObjectNode();
        ObjectNode normalizedExpected = (ObjectNode) expected.deepCopy();
        if (!normalizedExpected.has("statusCode") || !normalizedExpected.path("statusCode").canConvertToInt()) normalizedExpected.put("statusCode", 200);
        if (!normalizedExpected.path("assertions").isArray()) normalizedExpected.set("assertions", mapper.createArrayNode());

        try {
            testCase.setRequestJson(mapper.writeValueAsString(normalizedRequest));
            testCase.setExpectResult(mapper.writeValueAsString(normalizedExpected));
            testCase.setAssertionsJson(mapper.writeValueAsString(normalizedExpected.path("assertions")));
        } catch (Exception ex) {
            throw new IllegalStateException("测试用例 JSON 序列化失败", ex);
        }
        return testCase;
    }

    private Map<Long, EnumSet<Scenario>> coverage(List<TestCase> cases) {
        Map<Long, EnumSet<Scenario>> result = new HashMap<>();
        for (TestCase testCase : cases) {
            Scenario scenario = classify(testCase.getCaseType(), testCase.getCaseName(), testCase.getDescription());
            if (scenario != null) result.computeIfAbsent(testCase.getApiId(), ignored -> EnumSet.noneOf(Scenario.class)).add(scenario);
        }
        return result;
    }

    private List<Scenario> missingScenarios(EnumSet<Scenario> current) {
        List<Scenario> missing = new ArrayList<>();
        for (Scenario scenario : Scenario.values()) if (current == null || !current.contains(scenario)) missing.add(scenario);
        return missing;
    }

    private List<String> coverageGaps(List<ApiDefinition> apis, Map<Long, EnumSet<Scenario>> coverage) {
        List<String> gaps = new ArrayList<>();
        for (ApiDefinition api : apis) {
            List<String> missing = missingScenarios(coverage.get(api.getId())).stream().map(Enum::name).toList();
            if (!missing.isEmpty()) gaps.add(api.getMethod() + " " + api.getPath() + " 缺少 " + String.join("/", missing));
        }
        return gaps;
    }

    private Scenario classify(String type, String name, String description) {
        String value = ((type == null ? "" : type) + " " + (name == null ? "" : name) + " " + (description == null ? "" : description)).toLowerCase(Locale.ROOT);
        if (containsAny(value, "boundary", "edge", "limit", "边界", "临界", "最大值", "最小值", "上下限")) return Scenario.BOUNDARY;
        if (containsAny(value, "negative", "invalid", "error", "missing", "unauthorized", "forbidden", "异常", "非法", "缺失", "未授权", "无效", "错误", "越权")) return Scenario.NEGATIVE;
        if (containsAny(value, "positive", "normal", "success", "happy", "正常", "成功", "有效", "合法")) return Scenario.POSITIVE;
        return null;
    }

    private boolean containsAny(String value, String... words) {
        for (String word : words) if (value.contains(word)) return true;
        return false;
    }

    private String fingerprint(Long apiId, String request, String expected) {
        try {
            // Fingerprint the actual request + expected behavior, not the AI-provided label/category.
            // Two differently named scenarios that execute the same check are still duplicates.
            String canonical = apiId + "|" + canonicalJson(request) + "|" + canonicalJson(expected);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("生成测试用例指纹失败", ex);
        }
    }

    private String canonicalJson(String value) throws Exception {
        if (value == null || value.isBlank()) return "null";
        JsonNode node;
        try { node = mapper.readTree(value); } catch (Exception ex) { return value.trim(); }
        return mapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true).writeValueAsString(sort(node));
    }

    private JsonNode sort(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = mapper.createObjectNode();
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            Collections.sort(names);
            for (String name : names) sorted.set(name, sort(node.get(name)));
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode array = mapper.createArrayNode();
            node.forEach(child -> array.add(sort(child)));
            return array;
        }
        return node.deepCopy();
    }

    private JsonNode parseJson(String text) {
        try { return mapper.readTree(text); } catch (Exception ignored) { }
        String value = text == null ? "" : text.trim();
        int start = value.indexOf('{');
        int arrayStart = value.indexOf('[');
        if (start < 0 || (arrayStart >= 0 && arrayStart < start)) start = arrayStart;
        int end = Math.max(value.lastIndexOf('}'), value.lastIndexOf(']'));
        if (start >= 0 && end > start) {
            try { return mapper.readTree(value.substring(start, end + 1)); } catch (Exception ignored) { }
        }
        throw new IllegalStateException("Agent 返回内容不是有效 JSON，请重新生成");
    }

    private String safe(String value) {
        if (value == null) return "";
        return value.replaceAll("(?i)(\\\"?[^\\\"\\s]*(?:password|token|secret|authorization|api[_-]?key)[^\\\"\\s]*\\\"?\\s*:\\s*\\\")[^\\\"]*(\\\")", "$1***$2");
    }
    private boolean hasText(String value) { return value != null && !value.isBlank(); }
    private String limit(String value, int max) { if (value == null) return ""; return value.length() > max ? value.substring(0, max) : value; }
    private String shortText(String value) { if (value == null) return ""; return value.length() > 500 ? value.substring(0, 500) : value; }

    private enum Scenario { POSITIVE, NEGATIVE, BOUNDARY }
}
