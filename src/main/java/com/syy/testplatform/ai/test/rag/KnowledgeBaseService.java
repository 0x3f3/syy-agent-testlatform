package com.syy.testplatform.ai.test.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syy.testplatform.ai.test.entity.KnowledgeChunk;
import com.syy.testplatform.ai.test.repository.KnowledgeChunkRepository;
import com.syy.testplatform.ai.test.service.RedisPlatformCache;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class KnowledgeBaseService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    @Resource(name = "qwenEmbeddingModel")
    private EmbeddingModel embeddingModel;

    private final KnowledgeChunkRepository repository;
    private final ObjectMapper mapper;
    private final RedisPlatformCache cache;

    @Value("${app.rag.hybrid.enabled:true}")
    private boolean hybridEnabled;

    @Value("${app.rag.hybrid.vector-candidate-multiplier:3}")
    private int vectorCandidateMultiplier;

    @Value("${app.rag.hybrid.bm25-candidate-multiplier:3}")
    private int bm25CandidateMultiplier;

    @Value("${app.rag.hybrid.rrf-constant:60}")
    private int rrfConstant;

    public KnowledgeBaseService(KnowledgeChunkRepository repository, ObjectMapper mapper,
                                RedisPlatformCache cache) {
        this.repository = repository;
        this.mapper = mapper;
        this.cache = cache;
    }

    public void indexApiDocument(String source, String text) {
        indexText(source, text);
        indexBuiltInDocs();
    }

    public void indexBuiltInDocs() {
        for (String file : List.of("docs/api-testing-principles.md", "docs/openapi-test-design.md")) {
            String source = "builtin:" + file;
            if (!repository.findBySource(source).isEmpty()) continue;
            try {
                ClassPathResource resource = new ClassPathResource(file);
                if (resource.exists()) {
                    indexText(source, new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
                }
            } catch (Exception ex) {
                log.warn("Unable to index built-in RAG document {}: {}", file, ex.getMessage());
            }
        }
    }

    /** Indexes each distinct chunk once within a source. Cross-document duplicates are retained
     * in MySQL for traceability, detected by detectDuplicateDocuments(), and filtered at retrieval. */
    public void indexText(String source, String text) {
        if (text == null || text.isBlank()) return;
        String safeText = redact(text);
        List<String> chunks = deduplicateTexts(split(safeText, 800));
        repository.deleteBySource(source);
        cache.invalidateRagSearch();

        for (String chunk : chunks) {
            try {
                float[] vector = embeddingModel.embed(chunk).content().vector();
                KnowledgeChunk entity = new KnowledgeChunk();
                entity.setSource(source);
                entity.setContent(chunk);
                entity.setEmbeddingJson(mapper.writeValueAsString(vector));
                repository.save(entity);
            } catch (Exception ex) {
                log.warn("RAG embedding failed for source {}: {}", source, ex.getMessage());
                throw new IllegalStateException("RAG 向量化失败，请检查 DASHSCOPE_API_KEY 和 Embedding 模型配置", ex);
            }
        }

        if (source != null && source.startsWith("openapi:")) {
            List<RagSearchDiagnostics.DocumentDuplicate> duplicates = detectDuplicateDocuments();
            duplicates.stream()
                    .filter(group -> group.duplicateSources().contains(source))
                    .findFirst()
                    .ifPresent(group -> log.warn("Detected duplicate OpenAPI document source={} canonicalSource={} ; duplicate chunks will be removed during retrieval",
                            source, group.canonicalSource()));
        }
    }

    /** Normal Agent retrieval: Redis caches the already de-duplicated ranked context. */
    public String search(String query, int topK) {
        var cached = cache.getRagSearch("hybrid-rrf-v1:" + query, topK);
        if (cached.isPresent()) return cached.get();
        RetrievalResult retrieval = retrieve(query, topK);
        StringBuilder result = new StringBuilder();
        for (RagSearchDiagnostics.Hit hit : retrieval.hits()) {
            result.append("[排名: ").append(hit.rank())
                    .append(", 原始排名: ").append(hit.originalRank())
                    .append(", 来源: ").append(hit.source())
                    .append(", 相似度: ").append(String.format(Locale.ROOT, "%.4f", hit.similarity()))
                    .append(", 去重副本数: ").append(hit.duplicateCount()).append("]\n")
                    .append(hit.content()).append("\n\n");
        }
        if (result.length() == 0) {
            throw new IllegalStateException("RAG 向量检索没有返回有效知识块");
        }
        String value = result.toString();
        cache.putRagSearch("hybrid-rrf-v1:" + query, topK, value);
        return value;
    }

    /** Diagnostic retrieval bypasses Redis and returns unique Top-K with both raw and final ranks. */
    public RagSearchDiagnostics searchDebug(String query, int topK) {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("RAG 检索 query 不能为空");
        int limit = Math.max(1, Math.min(topK, 20));
        List<KnowledgeChunk> chunks = repository.findAll();
        if (chunks.isEmpty()) {
            return new RagSearchDiagnostics(query, limit, 0, 0, 0, 0, 0, 0,
                    true, List.of(), List.of());
        }
        RetrievalResult retrieval = retrieve(query, limit, chunks);
        return new RagSearchDiagnostics(
                query,
                limit,
                chunks.size(),
                retrieval.uniqueKnowledgeChunks(),
                retrieval.rawMatchedChunks(),
                retrieval.hits().size(),
                retrieval.duplicateChunksRemoved(),
                retrieval.documentDuplicates().size(),
                true,
                retrieval.documentDuplicates(),
                retrieval.hits()
        );
    }

    /** Reports exact duplicate OpenAPI sources using an order-sensitive fingerprint of their chunks. */
    public List<RagSearchDiagnostics.DocumentDuplicate> detectDuplicateDocuments() {
        Map<String, List<KnowledgeChunk>> bySource = repository.findAll().stream()
                .filter(chunk -> chunk.getSource() != null && chunk.getSource().startsWith("openapi:"))
                .collect(Collectors.groupingBy(KnowledgeChunk::getSource));

        Map<String, List<String>> sourcesByFingerprint = new LinkedHashMap<>();
        Map<String, Integer> chunkCounts = new HashMap<>();
        for (Map.Entry<String, List<KnowledgeChunk>> entry : bySource.entrySet()) {
            List<KnowledgeChunk> sourceChunks = entry.getValue().stream()
                    .sorted(Comparator.comparing(KnowledgeChunk::getId, Comparator.nullsLast(Long::compareTo)))
                    .toList();
            if (sourceChunks.isEmpty()) continue;
            StringBuilder signature = new StringBuilder();
            for (KnowledgeChunk chunk : sourceChunks) {
                signature.append(fingerprint(normalizeContent(chunk.getContent()))).append('\n');
            }
            String documentFingerprint = fingerprint(signature.toString());
            sourcesByFingerprint.computeIfAbsent(documentFingerprint, ignored -> new ArrayList<>()).add(entry.getKey());
            chunkCounts.put(documentFingerprint, sourceChunks.size());
        }

        List<RagSearchDiagnostics.DocumentDuplicate> duplicates = new ArrayList<>();
        sourcesByFingerprint.forEach((fingerprint, sources) -> {
            if (sources.size() < 2) return;
            sources.sort(KnowledgeBaseService::compareSource);
            duplicates.add(new RagSearchDiagnostics.DocumentDuplicate(
                    sources.get(0), List.copyOf(sources.subList(1, sources.size())),
                    chunkCounts.getOrDefault(fingerprint, 0), fingerprint));
        });
        duplicates.sort(Comparator.comparing(RagSearchDiagnostics.DocumentDuplicate::canonicalSource,
                KnowledgeBaseService::compareSource));
        return List.copyOf(duplicates);
    }

    public String retrievalDescription() {
        return hybridEnabled
                ? "Hybrid Search: Qwen Embedding + BM25 + RRF"
                : "Qwen Embedding + cosine similarity";
    }

    public int chunkCount() {
        return (int) repository.count();
    }

    private RetrievalResult retrieve(String query, int topK) {
        List<KnowledgeChunk> chunks = repository.findAll();
        if (chunks.isEmpty()) {
            throw new IllegalStateException("RAG 知识库为空，请先成功建立 OpenAPI 和内置测试知识向量索引");
        }
        return retrieve(query, topK, chunks);
    }

    /**
     * Hybrid retrieval:
     * 1) vector cosine ranking and BM25 lexical ranking are computed independently;
     * 2) top candidate lists are fused by Reciprocal Rank Fusion (RRF);
     * 3) exact-content duplicates are collapsed after fusion;
     * 4) final Top-K is cut from the unique fused ranking.
     *
     * similarity remains the original cosine similarity for backward compatibility.
     * originalRank remains the original vector rank; bm25Rank and rrfScore are additive diagnostics.
     */
    private RetrievalResult retrieve(String query, int topK, List<KnowledgeChunk> chunks) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("RAG 检索 query 不能为空");
        }
        int limit = Math.max(1, Math.min(topK, 20));
        int candidatePool = Math.max(limit, Math.min(200, limit * Math.max(1, vectorCandidateMultiplier)));

        float[] queryVector = embeddingModel.embed(query).content().vector();
        List<ScoredChunk> vectorScored = new ArrayList<>();
        for (KnowledgeChunk chunk : chunks) {
            try {
                float[] vector = mapper.readValue(chunk.getEmbeddingJson(), float[].class);
                vectorScored.add(new ScoredChunk(chunk, cosine(queryVector, vector),
                        fingerprint(normalizeContent(chunk.getContent()))));
            } catch (Exception ex) {
                log.debug("Skip malformed RAG vector id={}", chunk.getId());
            }
        }
        vectorScored.sort(Comparator.comparingDouble(ScoredChunk::score).reversed()
                .thenComparing(item -> item.chunk().getId(), Comparator.nullsLast(Long::compareTo)));

        Map<Long, Integer> vectorRanks = new HashMap<>();
        for (int i = 0; i < vectorScored.size(); i++) {
            Long id = vectorScored.get(i).chunk().getId();
            if (id != null) vectorRanks.put(id, i + 1);
        }

        int bm25Pool = Math.max(limit, Math.min(200, limit * Math.max(1, bm25CandidateMultiplier)));
        List<HybridRagRanker.Bm25Hit> bm25Hits = hybridEnabled
                ? HybridRagRanker.rankBm25(chunks, query, bm25Pool)
                : List.of();
        Map<Long, Integer> bm25Ranks = new HashMap<>();
        for (HybridRagRanker.Bm25Hit hit : bm25Hits) {
            if (hit.chunk().getId() != null) bm25Ranks.put(hit.chunk().getId(), hit.rank());
        }

        Map<Long, ScoredChunk> vectorById = new HashMap<>();
        for (ScoredChunk item : vectorScored) {
            if (item.chunk().getId() != null) vectorById.put(item.chunk().getId(), item);
        }

        Set<Long> candidateIds = new LinkedHashSet<>();
        vectorScored.stream().limit(candidatePool).forEach(item -> {
            if (item.chunk().getId() != null) candidateIds.add(item.chunk().getId());
        });
        if (hybridEnabled) {
            bm25Hits.stream().limit(bm25Pool).forEach(hit -> {
                if (hit.chunk().getId() != null) candidateIds.add(hit.chunk().getId());
            });
        }

        List<FusedChunk> fused = new ArrayList<>();
        for (Long id : candidateIds) {
            ScoredChunk vectorItem = vectorById.get(id);
            if (vectorItem == null) continue; // no usable embedding means it cannot be returned as an embedded RAG hit
            int vectorRank = vectorRanks.getOrDefault(id, Integer.MAX_VALUE);
            int bm25Rank = bm25Ranks.getOrDefault(id, 0);
            double rrf = hybridEnabled
                    ? HybridRagRanker.rrfScore(vectorRank, bm25Rank, Math.max(1, rrfConstant))
                    : 1.0 / (Math.max(1, rrfConstant) + vectorRank);
            fused.add(new FusedChunk(vectorItem, vectorRank, bm25Rank, rrf));
        }

        fused.sort(Comparator.comparingDouble(FusedChunk::rrfScore).reversed()
                .thenComparing(item -> item.scored().score(), Comparator.reverseOrder())
                .thenComparing(item -> item.scored().chunk().getId(), Comparator.nullsLast(Long::compareTo)));

        Map<String, List<RankedFusedChunk>> byContent = new LinkedHashMap<>();
        for (int i = 0; i < fused.size(); i++) {
            FusedChunk item = fused.get(i);
            byContent.computeIfAbsent(item.scored().contentFingerprint(), ignored -> new ArrayList<>())
                    .add(new RankedFusedChunk(item, i + 1));
        }

        List<UniqueChunk> unique = new ArrayList<>();
        byContent.values().forEach(copies -> {
            RankedFusedChunk best = copies.get(0); // highest RRF rank is canonical representative
            Long representativeId = best.fused().scored().chunk().getId();
            List<Long> duplicateIds = copies.stream()
                    .map(copy -> copy.fused().scored().chunk().getId())
                    .filter(Objects::nonNull)
                    .filter(id -> !Objects.equals(id, representativeId))
                    .toList();
            unique.add(new UniqueChunk(best.fused().scored(), best.fused().vectorRank(),
                    best.fused().bm25Rank(), best.fused().rrfScore(),
                    copies.size(), duplicateIds, best.fusedRank()));
        });
        unique.sort(Comparator.comparingDouble(UniqueChunk::rrfScore).reversed()
                .thenComparing(item -> item.scored().score(), Comparator.reverseOrder())
                .thenComparing(item -> item.scored().chunk().getId(), Comparator.nullsLast(Long::compareTo)));

        List<RagSearchDiagnostics.Hit> hits = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, unique.size()); i++) {
            UniqueChunk item = unique.get(i);
            hits.add(new RagSearchDiagnostics.Hit(
                    item.scored().chunk().getId(),
                    item.scored().chunk().getSource(),
                    round4(item.scored().score()),
                    i + 1,
                    item.originalRank(),
                    item.duplicateCount(),
                    item.duplicateChunkIds(),
                    item.scored().chunk().getContent(),
                    item.bm25Rank(),
                    round6(item.rrfScore()),
                    hybridEnabled ? "HYBRID_RRF" : "VECTOR_ONLY"
            ));
        }

        int uniqueCorpusChunks = (int) chunks.stream()
                .map(chunk -> fingerprint(normalizeContent(chunk.getContent())))
                .distinct().count();
        List<RagSearchDiagnostics.DocumentDuplicate> documentDuplicates = detectDuplicateDocumentsFrom(chunks);
        return new RetrievalResult(List.copyOf(hits), fused.size(), unique.size(), uniqueCorpusChunks,
                Math.max(0, fused.size() - unique.size()), documentDuplicates);
    }

    private List<RagSearchDiagnostics.DocumentDuplicate> detectDuplicateDocumentsFrom(List<KnowledgeChunk> chunks) {
        Map<String, List<KnowledgeChunk>> bySource = chunks.stream()
                .filter(chunk -> chunk.getSource() != null && chunk.getSource().startsWith("openapi:"))
                .collect(Collectors.groupingBy(KnowledgeChunk::getSource));
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        bySource.forEach((source, sourceChunks) -> {
            List<KnowledgeChunk> ordered = sourceChunks.stream()
                    .sorted(Comparator.comparing(KnowledgeChunk::getId, Comparator.nullsLast(Long::compareTo)))
                    .toList();
            if (ordered.isEmpty()) return;
            String signature = ordered.stream()
                    .map(chunk -> fingerprint(normalizeContent(chunk.getContent())))
                    .collect(Collectors.joining("\n"));
            String fp = fingerprint(signature);
            grouped.computeIfAbsent(fp, ignored -> new ArrayList<>()).add(source);
            counts.put(fp, ordered.size());
        });
        List<RagSearchDiagnostics.DocumentDuplicate> result = new ArrayList<>();
        grouped.forEach((fp, sources) -> {
            if (sources.size() < 2) return;
            sources.sort(KnowledgeBaseService::compareSource);
            result.add(new RagSearchDiagnostics.DocumentDuplicate(sources.get(0),
                    List.copyOf(sources.subList(1, sources.size())), counts.getOrDefault(fp, 0), fp));
        });
        result.sort(Comparator.comparing(RagSearchDiagnostics.DocumentDuplicate::canonicalSource,
                KnowledgeBaseService::compareSource));
        return List.copyOf(result);
    }

    private List<String> deduplicateTexts(List<String> chunks) {
        LinkedHashMap<String, String> unique = new LinkedHashMap<>();
        for (String chunk : chunks) unique.putIfAbsent(fingerprint(normalizeContent(chunk)), chunk);
        if (unique.size() != chunks.size()) {
            log.info("Removed {} repeated chunks within one document before embedding", chunks.size() - unique.size());
        }
        return List.copyOf(unique.values());
    }

    private String normalizeContent(String content) {
        if (content == null) return "";
        return Normalizer.normalize(content, Normalizer.Form.NFKC)
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String fingerprint(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("计算 RAG 内容指纹失败", ex);
        }
    }

    private static int compareSource(String left, String right) {
        Long leftId = sourceDocumentId(left);
        Long rightId = sourceDocumentId(right);
        if (leftId != null && rightId != null) return leftId.compareTo(rightId);
        if (leftId != null) return -1;
        if (rightId != null) return 1;
        return left.compareTo(right);
    }

    private static Long sourceDocumentId(String source) {
        if (source == null || !source.matches("openapi:\\d+")) return null;
        try { return Long.parseLong(source.substring("openapi:".length())); }
        catch (NumberFormatException ignored) { return null; }
    }

    private String redact(String text) {
        String safe = text.replaceAll("(?i)(\\\"?[^\\\"\\s]*(?:password|token|secret|authorization|api[_-]?key)[^\\\"\\s]*\\\"?\\s*:\\s*\\\")[^\\\"]*(\\\")", "$1***$2");
        return safe.replaceAll("(?i)(password|token|secret|api[_-]?key)=([^&\\s]+)", "$1=***");
    }

    private List<String> split(String text, int max) {
        List<String> result = new ArrayList<>();
        String normalized = text.replace("\r", "\n");
        for (String paragraph : normalized.split("\\n\\s*\\n")) {
            String paragraphText = paragraph.trim();
            if (paragraphText.isEmpty()) continue;
            for (int start = 0; start < paragraphText.length(); start += max) {
                int end = Math.min(paragraphText.length(), start + max);
                result.add(paragraphText.substring(start, end));
            }
        }
        if (result.isEmpty()) result.add(text.substring(0, Math.min(text.length(), max)));
        return result;
    }

    private double cosine(float[] a, float[] b) {
        if (a.length != b.length) return 0;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i];
        }
        return na == 0 || nb == 0 ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private double round4(double value) { return Math.round(value * 10000.0) / 10000.0; }
    private double round6(double value) { return Math.round(value * 1000000.0) / 1000000.0; }

    private record ScoredChunk(KnowledgeChunk chunk, double score, String contentFingerprint) {}
    private record FusedChunk(ScoredChunk scored, int vectorRank, int bm25Rank, double rrfScore) {}
    private record RankedFusedChunk(FusedChunk fused, int fusedRank) {}
    private record UniqueChunk(ScoredChunk scored, int originalRank, int bm25Rank, double rrfScore,
                               int duplicateCount, List<Long> duplicateChunkIds, int fusedOriginalRank) {}
    private record RetrievalResult(List<RagSearchDiagnostics.Hit> hits, int rawMatchedChunks,
                                   int uniqueMatchedChunks, int uniqueKnowledgeChunks,
                                   int duplicateChunksRemoved,
                                   List<RagSearchDiagnostics.DocumentDuplicate> documentDuplicates) {}
}
