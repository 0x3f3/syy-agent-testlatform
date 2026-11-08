package com.syy.testplatform.ai.test.rag;

import com.syy.testplatform.ai.test.entity.KnowledgeChunk;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight BM25 lexical ranker for the current MySQL-backed knowledge_chunk corpus.
 *
 * Tokenization is dependency-free:
 * - English identifiers, HTTP verbs, paths, schema names and numbers are kept as tokens.
 * - Chinese text is segmented into overlapping character bigrams, so terms such as
 *   "健康检查", "用户创建", "订单状态" can match without an external Chinese tokenizer.
 *
 * This is an in-memory BM25 implementation intended for the current small/medium corpus.
 * For a large corpus, migrate the lexical index to MySQL FULLTEXT/OpenSearch/Elasticsearch.
 */
public final class HybridRagRanker {
    private static final double K1 = 1.5;
    private static final double B = 0.75;
    private static final Pattern LATIN_OR_NUMBER = Pattern.compile("[a-z0-9_./{}:-]+");
    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff]+");

    private HybridRagRanker() {}

    public record Bm25Hit(KnowledgeChunk chunk, double score, int rank) {}

    public static List<Bm25Hit> rankBm25(List<KnowledgeChunk> documents, String query, int topK) {
        if (documents == null || documents.isEmpty() || query == null || query.isBlank() || topK <= 0) {
            return List.of();
        }

        List<Map<String, Integer>> termFrequencies = new ArrayList<>(documents.size());
        Map<String, Integer> documentFrequency = new HashMap<>();
        long totalLength = 0;

        for (KnowledgeChunk document : documents) {
            Map<String, Integer> tf = termFrequency(document == null ? "" : document.getContent());
            termFrequencies.add(tf);
            totalLength += tf.values().stream().mapToInt(Integer::intValue).sum();
            for (String term : tf.keySet()) documentFrequency.merge(term, 1, Integer::sum);
        }

        double averageLength = documents.isEmpty() ? 0.0 : (double) totalLength / documents.size();
        if (averageLength <= 0) return List.of();

        List<String> queryTerms = tokenize(query).stream().distinct().toList();
        if (queryTerms.isEmpty()) return List.of();

        List<Bm25Hit> scored = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            KnowledgeChunk document = documents.get(i);
            Map<String, Integer> tf = termFrequencies.get(i);
            int docLength = tf.values().stream().mapToInt(Integer::intValue).sum();
            double score = 0.0;

            for (String term : queryTerms) {
                int frequency = tf.getOrDefault(term, 0);
                if (frequency == 0) continue;
                int df = documentFrequency.getOrDefault(term, 0);
                double idf = Math.log(1.0 + (documents.size() - df + 0.5) / (df + 0.5));
                double denominator = frequency + K1 * (1.0 - B + B * docLength / averageLength);
                score += idf * (frequency * (K1 + 1.0)) / denominator;
            }
            if (score > 0.0) scored.add(new Bm25Hit(document, score, 0));
        }

        scored.sort(Comparator.comparingDouble(Bm25Hit::score).reversed()
                .thenComparing(hit -> hit.chunk().getId(), Comparator.nullsLast(Long::compareTo)));

        List<Bm25Hit> ranked = new ArrayList<>(Math.min(topK, scored.size()));
        for (int i = 0; i < Math.min(topK, scored.size()); i++) {
            Bm25Hit hit = scored.get(i);
            ranked.add(new Bm25Hit(hit.chunk(), hit.score(), i + 1));
        }
        return List.copyOf(ranked);
    }

    public static double rrfScore(int vectorRank, int bm25Rank, int rrfConstant) {
        double score = 0.0;
        if (vectorRank > 0 && vectorRank < Integer.MAX_VALUE) {
            score += 1.0 / (rrfConstant + vectorRank);
        }
        if (bm25Rank > 0 && bm25Rank < Integer.MAX_VALUE) {
            score += 1.0 / (rrfConstant + bm25Rank);
        }
        return score;
    }

    static List<String> tokenize(String input) {
        if (input == null || input.isBlank()) return List.of();
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        List<String> tokens = new ArrayList<>();

        Matcher latinMatcher = LATIN_OR_NUMBER.matcher(normalized);
        while (latinMatcher.find()) {
            String token = latinMatcher.group().trim();
            if (!token.isEmpty()) tokens.add(token);
        }

        Matcher cjkMatcher = CJK.matcher(normalized);
        while (cjkMatcher.find()) {
            String run = cjkMatcher.group();
            if (run.length() == 1) {
                tokens.add(run);
            } else {
                for (int i = 0; i < run.length() - 1; i++) {
                    tokens.add(run.substring(i, i + 2));
                }
            }
        }
        return tokens;
    }

    private static Map<String, Integer> termFrequency(String content) {
        Map<String, Integer> tf = new HashMap<>();
        for (String token : tokenize(content)) tf.merge(token, 1, Integer::sum);
        return tf;
    }
}
