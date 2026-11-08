package com.syy.testplatform.ai.test.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

@Component
public class SensitiveDataSanitizer {
    private final ObjectMapper mapper;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public SensitiveDataSanitizer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String sanitizeJson(String value) {
        if (value == null || value.isBlank()) return value;
        try {
            JsonNode root = mapper.readTree(value);
            redact(root);
            return mapper.writeValueAsString(root);
        } catch (Exception ignored) {
            return value.replaceAll("(?i)(password|token|secret|authorization|api[_-]?key)(\\s*[:=]\\s*)([^,\\s&]+)", "$1$2***");
        }
    }


    /**
     * Keeps the uploaded document in its original JSON/YAML representation while
     * masking credential-like fields before the content is persisted or exposed.
     */
    public String sanitizeOpenApiDocument(String value, String format) {
        if (value == null || value.isBlank()) return value;
        boolean yaml = "YAML".equalsIgnoreCase(format);
        ObjectMapper selected = yaml ? yamlMapper : mapper;
        try {
            JsonNode root = selected.readTree(value);
            redact(root);
            return selected.writeValueAsString(root);
        } catch (Exception ex) {
            throw new IllegalArgumentException("OpenAPI 原始文档无法安全解析或脱敏", ex);
        }
    }

    private void redact(JsonNode node) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (isSensitive(field.getKey())) {
                    ((ObjectNode) node).put(field.getKey(), "***");
                } else {
                    redact(field.getValue());
                }
            }
        } else if (node.isArray()) {
            node.forEach(this::redact);
        }
    }

    private boolean isSensitive(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.contains("password") || normalized.contains("token")
                || normalized.contains("secret") || normalized.contains("authorization")
                || normalized.contains("cookie") || normalized.contains("api_key")
                || normalized.contains("api-key") || normalized.contains("apikey");
    }
}
