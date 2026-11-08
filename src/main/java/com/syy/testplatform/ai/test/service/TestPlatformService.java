package com.syy.testplatform.ai.test.service;

import com.syy.testplatform.ai.test.entity.ApiDefinition;
import com.syy.testplatform.ai.test.entity.ApiDocument;
import com.syy.testplatform.ai.test.parser.OpenApiParser;
import com.syy.testplatform.ai.test.parser.ParsedOpenApi;
import com.syy.testplatform.ai.test.rag.KnowledgeBaseService;
import com.syy.testplatform.ai.test.repository.ApiDefinitionRepository;
import com.syy.testplatform.ai.test.repository.ApiDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Service
public class TestPlatformService {
    private final OpenApiParser parser;
    private final ApiDocumentRepository documentRepository;
    private final ApiDefinitionRepository apiRepository;
    private final KnowledgeBaseService knowledgeBase;
    private final SensitiveDataSanitizer sanitizer;
    private final RedisPlatformCache cache;

    public TestPlatformService(OpenApiParser parser,
                               ApiDocumentRepository documentRepository,
                               ApiDefinitionRepository apiRepository,
                               KnowledgeBaseService knowledgeBase,
                               SensitiveDataSanitizer sanitizer,
                               RedisPlatformCache cache) {
        this.parser = parser;
        this.documentRepository = documentRepository;
        this.apiRepository = apiRepository;
        this.knowledgeBase = knowledgeBase;
        this.sanitizer = sanitizer;
        this.cache = cache;
    }

    public ApiImportResult upload(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请上传非空的 OpenAPI JSON/YAML 文件");
        }

        String filename = file.getOriginalFilename();
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (StringUtils.hasText(filename)
                && !(lower.endsWith(".json") || lower.endsWith(".yaml") || lower.endsWith(".yml"))) {
            throw new IllegalArgumentException("仅支持 .json、.yaml、.yml 格式的 Swagger/OpenAPI 文档");
        }

        byte[] bytes = file.getBytes();
        String sourceContent = new String(bytes, StandardCharsets.UTF_8);
        String firstNonWhitespace = sourceContent.stripLeading();
        String format = lower.endsWith(".yaml") || lower.endsWith(".yml")
                || (!firstNonWhitespace.isEmpty() && !firstNonWhitespace.startsWith("{") && !firstNonWhitespace.startsWith("["))
                ? "YAML" : "JSON";

        ParsedOpenApi parsed = parser.parse(new ByteArrayInputStream(bytes));

        ApiDocument doc = new ApiDocument();
        doc.setName(StringUtils.hasText(filename) ? filename : "openapi." + format.toLowerCase(Locale.ROOT));
        doc.setTitle(parsed.title());
        doc.setVersion(parsed.version());
        doc.setSpecificationVersion(parsed.specificationVersion());
        doc.setServerUrl(parsed.serverUrl());
        doc.setRawDocument(sanitizer.sanitizeOpenApiDocument(sourceContent, format));
        doc.setRawDocumentFormat(format);
        doc = documentRepository.save(doc);

        for (ApiDefinition api : parsed.apis()) {
            api.setDocumentId(doc.getId());
            api.setParametersJson(sanitizer.sanitizeJson(api.getParametersJson()));
            api.setRequestBodySchemaJson(sanitizer.sanitizeJson(api.getRequestBodySchemaJson()));
            api.setRequestExampleJson(sanitizer.sanitizeJson(api.getRequestExampleJson()));
            api.setResponsesJson(sanitizer.sanitizeJson(api.getResponsesJson()));
            api.setSecurityJson(sanitizer.sanitizeJson(api.getSecurityJson()));
            api.setRawOperationJson(sanitizer.sanitizeJson(api.getRawOperationJson()));
        }
        List<ApiDefinition> saved = apiRepository.saveAll(parsed.apis());

        StringBuilder knowledgeText = new StringBuilder("OpenAPI 文档: ")
                .append(parsed.title()).append(" 版本: ").append(parsed.version()).append("\n");
        for (ApiDefinition api : saved) {
            knowledgeText.append(api.getMethod()).append(" ").append(api.getPath()).append(" ")
                    .append(api.getDescription()).append("\n参数:")
                    .append(api.getParametersJson()).append("\n请求Schema:")
                    .append(api.getRequestBodySchemaJson()).append("\n响应:")
                    .append(api.getResponsesJson()).append("\n");
        }

        String ragStatus = "待建立向量索引";
        try {
            knowledgeBase.indexApiDocument("openapi:" + doc.getId(), knowledgeText.toString());
            ragStatus = "向量索引已写入 MySQL";
        } catch (Exception ex) {
            ragStatus = "接口已保存，RAG 索引失败：" + ex.getMessage();
        }

        cache.putDocument(doc);
        return new ApiImportResult(doc, saved, saved.size(), ragStatus);
    }

    public List<ApiDocument> documents() {
        return documentRepository.findAllByOrderByImportedAtDesc();
    }

    public List<ApiDefinition> apis(Long documentId) {
        return apiRepository.findByDocumentIdOrderByIdAsc(documentId);
    }

    public ApiDocument getDocument(Long id) {
        return cache.getDocument(id).orElseGet(() -> {
            ApiDocument document = documentRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("OpenAPI 文档不存在：" + id));
            cache.putDocument(document);
            return document;
        });
    }
}
