package com.syy.testplatform.ai.test.rag;

import java.util.List;

public record RagSearchDiagnostics(
        String query,
        int requestedTopK,
        int totalKnowledgeChunks,
        int matchedChunks,
        boolean cacheBypassed,
        List<Hit> results
) {
    public record Hit(
            Long chunkId,
            String source,
            double similarity,
            String content
    ) {}
}
