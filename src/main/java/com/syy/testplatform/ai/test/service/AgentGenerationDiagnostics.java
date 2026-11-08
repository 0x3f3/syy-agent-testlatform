
package com.syy.testplatform.ai.test.service;

import com.syy.testplatform.ai.test.rag.RagSearchDiagnostics;

import java.util.List;
import java.util.Map;

public record AgentGenerationDiagnostics(
        Long documentId,
        boolean agentInvoked,
        int apiCount,
        int caseCountBefore,
        int caseCountAfter,
        int newlyAddedCaseCount,
        Map<String, Integer> scenarioCounts,
        boolean coverageComplete,
        ScenarioCoverageResponse coverage,
        List<TestCaseResponse> cases,
        RagSearchDiagnostics ragContext,
        String ragDiagnosticError,
        List<String> qualityChecks
) {}
