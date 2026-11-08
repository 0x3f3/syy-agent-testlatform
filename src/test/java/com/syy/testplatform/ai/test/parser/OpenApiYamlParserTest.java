package com.syy.testplatform.ai.test.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class OpenApiYamlParserTest {
    @Test
    void parsesOpenApiYamlAndResolvesBasicOperationFields() throws Exception {
        String yaml = """
                openapi: 3.0.3
                info:
                  title: YAML Sample API
                  version: 1.2.0
                servers:
                  - url: https://api.example.test/v1
                paths:
                  /health:
                    get:
                      operationId: healthCheck
                      summary: Health check
                      responses:
                        '200':
                          description: OK
                """;

        ParsedOpenApi parsed = new OpenApiParser(new ObjectMapper()).parse(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        assertEquals("YAML Sample API", parsed.title());
        assertEquals("OpenAPI 3.0.3", parsed.specificationVersion());
        assertEquals("https://api.example.test/v1", parsed.serverUrl());
        assertEquals(1, parsed.apis().size());
        assertEquals("GET", parsed.apis().get(0).getMethod());
        assertEquals("/health", parsed.apis().get(0).getPath());
    }

    @Test
    void stillParsesJsonThroughTheSameParser() throws Exception {
        String json = "{\"openapi\":\"3.0.3\",\"info\":{\"title\":\"JSON API\",\"version\":\"1\"},\"paths\":{\"/ping\":{\"get\":{\"responses\":{\"200\":{\"description\":\"ok\"}}}}}}";
        ParsedOpenApi parsed = new OpenApiParser(new ObjectMapper()).parse(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        assertEquals("JSON API", parsed.title());
        assertEquals(1, parsed.apis().size());
    }
}
