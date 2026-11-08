package com.syy.aicodehelper.ai.test.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syy.aicodehelper.ai.test.entity.KnowledgeChunk;
import com.syy.aicodehelper.ai.test.repository.KnowledgeChunkRepository;
import com.syy.aicodehelper.ai.test.service.RedisPlatformCache;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class KnowledgeBaseDeduplicationTest {

    @Test
    @SuppressWarnings("unchecked")
    void searchReturnsUniqueTopKAndPreservesRawRankAndSimilarity() throws Exception {
        KnowledgeChunkRepository repository = mock(KnowledgeChunkRepository.class);
        RedisPlatformCache cache = mock(RedisPlatformCache.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        Response<Embedding> response = mock(Response.class);
        Embedding embedding = mock(Embedding.class);
        when(embeddingModel.embed(anyString())).thenReturn(response);
        when(response.content()).thenReturn(embedding);
        when(embedding.vector()).thenReturn(new float[]{1.0f, 0.0f});

        KnowledgeChunk firstCopy = chunk(10L, "openapi:1", "same operation", "[1.0,0.0]");
        KnowledgeChunk secondCopy = chunk(20L, "openapi:2", "same operation", "[1.0,0.0]");
        KnowledgeChunk distinct = chunk(30L, "openapi:3", "different operation", "[0.0,1.0]");
        when(repository.findAll()).thenReturn(List.of(firstCopy, secondCopy, distinct));

        KnowledgeBaseService service = new KnowledgeBaseService(repository, new ObjectMapper(), cache);
        ReflectionTestUtils.setField(service, "embeddingModel", embeddingModel);

        RagSearchDiagnostics result = service.searchDebug("find operation", 2);

        assertEquals(3, result.totalKnowledgeChunks());
        assertEquals(2, result.uniqueKnowledgeChunks());
        assertEquals(3, result.rawMatchedChunks());
        assertEquals(2, result.matchedChunks());
        assertEquals(1, result.duplicateChunksRemoved());
        assertEquals(1, result.duplicateDocumentGroups());
        assertEquals(2, result.results().size());

        RagSearchDiagnostics.Hit first = result.results().get(0);
        assertEquals(10L, first.chunkId());
        assertEquals(1, first.rank());
        assertEquals(1, first.originalRank());
        assertEquals(2, first.duplicateCount());
        assertEquals(List.of(20L), first.duplicateChunkIds());
        assertEquals(1.0, first.similarity(), 0.0001);

        RagSearchDiagnostics.Hit second = result.results().get(1);
        assertEquals(30L, second.chunkId());
        assertEquals(2, second.rank());
        assertEquals(3, second.originalRank());
        assertEquals(0.0, second.similarity(), 0.0001);

        RagSearchDiagnostics.DocumentDuplicate duplicate = result.documentDuplicates().get(0);
        assertEquals("openapi:1", duplicate.canonicalSource());
        assertEquals(List.of("openapi:2"), duplicate.duplicateSources());
    }

    private KnowledgeChunk chunk(Long id, String source, String content, String vectorJson) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setId(id);
        chunk.setSource(source);
        chunk.setContent(content);
        chunk.setEmbeddingJson(vectorJson);
        return chunk;
    }
}
