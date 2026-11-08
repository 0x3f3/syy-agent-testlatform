package com.syy.testplatform.ai.test.rag;

import java.util.List;

/** Detailed retrieval trace. rank is the position after content de-duplication;
 * originalRank is the position in the unfiltered similarity ranking. */
public record RagSearchDiagnostics(
        String query,
        int requestedTopK,
        int totalKnowledgeChunks,
        int uniqueKnowledgeChunks,
        int rawMatchedChunks,
        int matchedChunks,
        int duplicateChunksRemoved,
        int duplicateDocumentGroups,
        boolean cacheBypassed,
        List<DocumentDuplicate> documentDuplicates,
        List<Hit> results
) {
    public record Hit(
            Long chunkId,
            String source,
            double similarity,
            int rank,
            int originalRank,
            int duplicateCount,
            List<Long> duplicateChunkIds,
            String content,
            int bm25Rank,
            double rrfScore,
            String retrievalMethod
    ) {}

    public record DocumentDuplicate(
            String canonicalSource,
            List<String> duplicateSources,
            int chunkCount,
            String fingerprint
    ) {}
}
