package com.syy.testplatform.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.syy.testplatform.ai.test.entity.ApiDefinition;
import com.syy.testplatform.ai.test.entity.ApiDocument;
import com.syy.testplatform.ai.test.service.ApiImportResult;
import com.syy.testplatform.ai.test.service.TestPlatformService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Tag(name = "OpenAPI 文档管理")
@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestPlatformController {
    private final TestPlatformService service;

    @Operation(summary = "上传并解析 Swagger/OpenAPI JSON 或 YAML 文档")
    @PostMapping("/swagger/upload")
    public ApiImportResult upload(@RequestParam("file") MultipartFile file) throws Exception {
        return service.upload(file);
    }

    @GetMapping("/documents")
    public List<ApiDocument> documents() {
        return service.documents();
    }

    @GetMapping("/apis")
    public List<ApiDefinition> apis(@RequestParam Long documentId) {
        return service.apis(documentId);
    }

    @GetMapping("/documents/{id}")
    public ApiDocument document(@PathVariable Long id) {
        return service.getDocument(id);
    }

    @Operation(summary = "读取已上传文档的脱敏原始 OpenAPI 内容，保留 JSON/YAML 格式")
    @GetMapping("/documents/{id}/raw")
    public ResponseEntity<String> rawDocument(@PathVariable Long id) {
        ApiDocument document = service.getDocument(id);
        String format = document.getRawDocumentFormat() == null ? "JSON" : document.getRawDocumentFormat();
        MediaType contentType = "YAML".equalsIgnoreCase(format)
                ? MediaType.parseMediaType("application/yaml;charset=UTF-8")
                : new MediaType("application", "json", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"openapi-" + id + "." +
                        ("YAML".equalsIgnoreCase(format) ? "yaml" : "json") + "\"")
                .body(document.getRawDocument());
    }
}
