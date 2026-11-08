package com.syy.testplatform.ai.test.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syy.testplatform.ai.test.entity.ApiDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.RedisCallback;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

/** Redis cache adapter. Redis outages are non-fatal: MySQL/LLM remain the source of truth. */
@Component
public class RedisPlatformCache {
    private static final Logger log = LoggerFactory.getLogger(RedisPlatformCache.class);
    private static final String PREFIX = "syy:v11:";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final boolean enabled;
    private final Duration documentTtl;
    private final Duration ragTtl;
    private final Duration taskTtl;

    public RedisPlatformCache(StringRedisTemplate redis, ObjectMapper mapper,
                              @Value("${app.redis.cache-enabled:true}") boolean enabled,
                              @Value("${app.redis.document-ttl:30m}") Duration documentTtl,
                              @Value("${app.redis.rag-ttl:10m}") Duration ragTtl,
                              @Value("${app.redis.task-status-ttl:24h}") Duration taskTtl) {
        this.redis = redis;
        this.mapper = mapper;
        this.enabled = enabled;
        this.documentTtl = documentTtl;
        this.ragTtl = ragTtl;
        this.taskTtl = taskTtl;
    }

    public Optional<ApiDocument> getDocument(Long id) {
        Optional<CachedApiDocument> cached = read("document:" + id, CachedApiDocument.class);
        return cached.map(CachedApiDocument::toEntity);
    }

    public void putDocument(ApiDocument document) {
        if (document == null || document.getId() == null) return;
        CachedApiDocument cached = new CachedApiDocument(document.getId(), document.getName(), document.getTitle(),
                document.getVersion(), document.getServerUrl(), document.getSpecificationVersion(),
                document.getRawDocument(), document.getRawDocumentFormat(), document.getImportedAt());
        write("document:" + document.getId(), cached, documentTtl);
    }

    public Optional<String> getRagSearch(String query, int topK) {
        return read("rag:" + ragKey(query, topK), String.class);
    }

    public void putRagSearch(String query, int topK, String result) {
        write("rag:" + ragKey(query, topK), result, ragTtl);
    }

    public void invalidateRagSearch() {
        if (!enabled) return;
        try {
            // A version key avoids KEYS/SCAN over the entire Redis database.
            redis.opsForValue().increment(PREFIX + "rag:version");
        } catch (Exception ex) {
            log.debug("Redis RAG cache invalidation skipped: {}", ex.getMessage());
        }
    }

    public void putTaskProgress(TaskProgressResponse progress) {
        if (progress == null || progress.taskId() == null) return;
        write("task-progress:" + progress.taskId(), progress, taskTtl);
    }

    public Optional<TaskProgressResponse> getTaskProgress(Long taskId) {
        return read("task-progress:" + taskId, TaskProgressResponse.class);
    }

    public boolean ping() {
        if (!enabled) return false;
        try { return "PONG".equalsIgnoreCase(redis.execute((RedisCallback<String>) connection -> connection.ping())); }
        catch (Exception ex) { return false; }
    }

    private <T> Optional<T> read(String key, Class<T> type) {
        if (!enabled) return Optional.empty();
        try {
            String value = redis.opsForValue().get(PREFIX + key);
            return value == null ? Optional.empty() : Optional.of(mapper.readValue(value, type));
        } catch (Exception ex) {
            log.debug("Redis cache read skipped for {}: {}", key, ex.getMessage());
            return Optional.empty();
        }
    }

    private void write(String key, Object value, Duration ttl) {
        if (!enabled || value == null) return;
        try {
            redis.opsForValue().set(PREFIX + key, mapper.writeValueAsString(value), ttl);
        } catch (Exception ex) {
            log.debug("Redis cache write skipped for {}: {}", key, ex.getMessage());
        }
    }

    private String ragKey(String query, int topK) {
        String version = "0";
        if (enabled) {
            try {
                String value = redis.opsForValue().get(PREFIX + "rag:version");
                if (value != null) version = value;
            } catch (Exception ex) {
                log.debug("Redis RAG cache version unavailable: {}", ex.getMessage());
            }
        }
        return version + ":" + topK + ":" + sha256(query == null ? "" : query);
    }

    public record CachedApiDocument(Long id, String name, String title, String version, String serverUrl,
                                     String specificationVersion, String rawDocument, String rawDocumentFormat, LocalDateTime importedAt) {
        ApiDocument toEntity() {
            ApiDocument document = new ApiDocument();
            document.setId(id); document.setName(name); document.setTitle(title); document.setVersion(version);
            document.setServerUrl(serverUrl); document.setSpecificationVersion(specificationVersion);
            document.setRawDocument(rawDocument); document.setRawDocumentFormat(rawDocumentFormat == null ? "JSON" : rawDocumentFormat); document.setImportedAt(importedAt);
            return document;
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("生成缓存键失败", ex);
        }
    }
}
