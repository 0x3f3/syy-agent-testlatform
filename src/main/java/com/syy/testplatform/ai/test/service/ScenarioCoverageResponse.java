package com.syy.testplatform.ai.test.service;

import java.util.List;
import java.util.Map;

public record ScenarioCoverageResponse(
        Long documentId,
        int apiCount,
        int totalCaseCount,
        boolean complete,
        List<ApiCoverage> apis
) {
    public record ApiCoverage(
            Long apiId,
            String endpoint,
            int totalCases,
            Map<String, Integer> scenarioCounts,
            List<String> missingScenarios,
            boolean complete
    ) {}
}
