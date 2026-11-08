package com.syy.testplatform.ai.test.service;

import java.time.LocalDateTime;

public record TestCaseResponse(
        Long id,
        Long apiId,
        String caseName,
        String caseType,
        String priority,
        String description,
        String requestJson,
        String expectResult,
        String assertionsJson,
        LocalDateTime createdAt
) {}
