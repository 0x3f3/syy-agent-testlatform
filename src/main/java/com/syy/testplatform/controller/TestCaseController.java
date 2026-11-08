package com.syy.testplatform.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.syy.testplatform.ai.test.entity.TestCase;
import com.syy.testplatform.ai.test.repository.TestCaseRepository;
import com.syy.testplatform.ai.test.service.SensitiveDataSanitizer;
import com.syy.testplatform.ai.test.service.ScenarioCoverageResponse;
import com.syy.testplatform.ai.test.service.TestCaseGenerationService;
import com.syy.testplatform.ai.test.service.TestCaseResponse;
import com.syy.testplatform.ai.test.service.AgentGenerationDiagnostics;
import com.syy.testplatform.ai.test.rag.RagSearchDiagnostics;
import java.util.LinkedHashMap;
import java.util.Map;
import com.syy.testplatform.ai.test.service.TestPlatformService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "测试用例与场景覆盖")
@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestCaseController {
    private final TestCaseGenerationService generationService;
    private final TestCaseRepository caseRepository;
    private final TestPlatformService platformService;
    private final SensitiveDataSanitizer sanitizer;

    @PostMapping("/documents/{documentId}/generate-cases")
    public List<TestCaseResponse> generate(@PathVariable Long documentId) {
        platformService.getDocument(documentId);
        return generationService.generateForDocument(documentId).stream().map(this::toResponse).toList();
    }


    @PostMapping("/documents/{documentId}/generate-cases/diagnostics")
    public AgentGenerationDiagnostics generateWithDiagnostics(@PathVariable Long documentId) {
        platformService.getDocument(documentId);
        ScenarioCoverageResponse before = generationService.coverageForDocument(documentId);
        boolean agentInvoked = !before.complete();

        List<TestCase> generated = generationService.generateForDocument(documentId);
        ScenarioCoverageResponse after = generationService.coverageForDocument(documentId);

        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("POSITIVE", 0);
        counts.put("NEGATIVE", 0);
        counts.put("BOUNDARY", 0);
        generated.forEach(item -> counts.computeIfPresent(item.getCaseType(),
                (key, value) -> value + 1));

        RagSearchDiagnostics ragContext = null;
        String ragError = null;
        try {
            ragContext = generationService.previewRagContextForDocument(documentId);
        } catch (Exception ex) {
            ragError = ex.getMessage();
        }

        List<String> qualityChecks = List.of(
                "每个已解析接口至少覆盖 POSITIVE、NEGATIVE、BOUNDARY 三类场景",
                "生成结果必须绑定到当前文档中的真实 apiId / method / path",
                "请求参数、请求体和预期响应应与 OpenAPI 契约一致，需人工抽查",
                "第二次执行应复用已有用例，避免重复启用相同请求与断言"
        );

        return new AgentGenerationDiagnostics(
                documentId,
                agentInvoked,
                after.apiCount(),
                before.totalCaseCount(),
                after.totalCaseCount(),
                Math.max(0, after.totalCaseCount() - before.totalCaseCount()),
                counts,
                after.complete(),
                after,
                generated.stream().map(this::toResponse).toList(),
                ragContext,
                ragError,
                qualityChecks
        );
    }

    @GetMapping("/documents/{documentId}/coverage")
    public ScenarioCoverageResponse coverage(@PathVariable Long documentId) {
        platformService.getDocument(documentId);
        return generationService.coverageForDocument(documentId);
    }

    @GetMapping("/cases")
    public List<TestCaseResponse> cases(@RequestParam(required = false) Long apiId,
                                        @RequestParam(required = false) Long documentId) {
        List<TestCase> rows;
        if (apiId != null) {
            rows = caseRepository.findByApiIdAndEnabledTrueOrderByIdAsc(apiId);
        } else if (documentId != null) {
            rows = caseRepository.findByApiIdInAndEnabledTrueOrderByIdAsc(
                    platformService.apis(documentId).stream().map(api -> api.getId()).toList()
            );
        } else {
            rows = caseRepository.findAll().stream().filter(item -> !Boolean.FALSE.equals(item.getEnabled())).toList();
        }
        return rows.stream().map(this::toResponse).toList();
    }

    @DeleteMapping("/cases/{caseId}")
    public void delete(@PathVariable Long caseId) {
        caseRepository.deleteById(caseId);
    }

    private TestCaseResponse toResponse(TestCase item) {
        return new TestCaseResponse(
                item.getId(), item.getApiId(), item.getCaseName(), item.getCaseType(),
                item.getPriority(), item.getDescription(), sanitizer.sanitizeJson(item.getRequestJson()),
                sanitizer.sanitizeJson(item.getExpectResult()), sanitizer.sanitizeJson(item.getAssertionsJson()),
                item.getCreatedAt()
        );
    }
}
