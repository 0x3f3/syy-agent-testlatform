package com.syy.testplatform.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.syy.testplatform.ai.test.rag.KnowledgeBaseService;
import com.syy.testplatform.ai.test.rag.RagSearchDiagnostics;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "RAG 知识库")
@RestController
@RequestMapping("/test/knowledge")
@RequiredArgsConstructor
public class TestKnowledgeController {
    private final KnowledgeBaseService knowledgeBase;

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("chunkCount", knowledgeBase.chunkCount(),
                "storage", "MySQL", "retrieval", "Qwen Embedding + cosine similarity");
    }

    @PostMapping("/reindex/builtin")
    public Map<String, Object> reindexBuiltin() {
        knowledgeBase.indexBuiltInDocs();
        return status();
    }

    @Operation(summary = "执行一次真实向量检索并返回 Top-K 命中证据（绕过 Redis 缓存，便于评估 RAG）")
    @GetMapping("/search")
    public RagSearchDiagnostics search(@RequestParam String query,
                                       @RequestParam(defaultValue = "5") int topK) {
        return knowledgeBase.searchDebug(query, topK);
    }
}
