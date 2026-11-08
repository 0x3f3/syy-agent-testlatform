package com.syy.testplatform.ai.test.rag;

import com.syy.testplatform.ai.test.entity.KnowledgeChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HybridRagRankerTest {

    private KnowledgeChunk chunk(long id, String content) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setId(id);
        chunk.setContent(content);
        chunk.setSource("test:" + id);
        return chunk;
    }

    @Test
    void bm25ShouldFindChineseHealthCheckAndExactApiPath() {
        List<KnowledgeChunk> corpus = List.of(
                chunk(1, "GET /api/v1/health 健康检查接口，正常响应状态码为 200。"),
                chunk(2, "POST /api/v1/users 创建用户，需要 username 和 email。"),
                chunk(3, "DELETE /api/v1/users/{userId} 删除用户。")
        );

        List<HybridRagRanker.Bm25Hit> hits =
                HybridRagRanker.rankBm25(corpus, "健康检查接口的正常响应是什么？", 3);

        assertFalse(hits.isEmpty());
        assertEquals(1L, hits.get(0).chunk().getId());
    }

    @Test
    void bm25ShouldMatchHttpVerbAndPathTokens() {
        List<KnowledgeChunk> corpus = List.of(
                chunk(1, "GET /api/v1/users 查询用户列表。"),
                chunk(2, "PUT /api/v1/users/{userId} 更新用户信息。"),
                chunk(3, "DELETE /api/v1/users/{userId} 删除用户。")
        );

        List<HybridRagRanker.Bm25Hit> hits =
                HybridRagRanker.rankBm25(corpus, "PUT /api/v1/users/{userId}", 3);

        assertFalse(hits.isEmpty());
        assertEquals(2L, hits.get(0).chunk().getId());
    }

    @Test
    void rrfShouldRewardAgreementBetweenVectorAndBm25() {
        double agreement = HybridRagRanker.rrfScore(1, 1, 60);
        double vectorOnly = HybridRagRanker.rrfScore(1, Integer.MAX_VALUE, 60);
        double lowerRankAgreement = HybridRagRanker.rrfScore(5, 5, 60);

        assertTrue(agreement > vectorOnly);
        assertTrue(agreement > lowerRankAgreement);
    }

    @Test
    void bm25ShouldReturnEmptyForNoLexicalMatch() {
        List<KnowledgeChunk> corpus = List.of(
                chunk(1, "GET /api/v1/health 健康检查接口。"),
                chunk(2, "POST /api/v1/users 创建用户。")
        );

        assertTrue(HybridRagRanker.rankBm25(corpus, "完全不存在的火星字段", 5).isEmpty());
    }
}
