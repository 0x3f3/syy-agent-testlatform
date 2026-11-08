package com.syy.testplatform.ai.test.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syy.testplatform.test.executor.HttpExecutionResult;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AssertionEvaluatorTest {
    private final AssertionEvaluator evaluator = new AssertionEvaluator(new ObjectMapper());

    @Test
    void passesStatusAndJsonPathAssertions() {
        String expected = """
                {"statusCode":200,"assertions":[
                  {"type":"JSON_PATH_EXISTS","expression":"$.data.id"},
                  {"type":"JSON_PATH_EQUALS","expression":"$.data.status","expectedValue":"ACTIVE"}
                ]}
                """;
        HttpExecutionResult actual = new HttpExecutionResult("http://localhost/x", "GET", 200,
                Map.of("Content-Type", List.of("application/json")),
                "{\"data\":{\"id\":1,\"status\":\"ACTIVE\"}}", 12, null);
        var result = evaluator.evaluate(expected, actual);
        assertTrue(result.pass());
        assertEquals(100, result.score());
    }

    @Test
    void failsStatusAndJsonPathAssertions() {
        String expected = "{\"statusCode\":200,\"assertions\":[{\"type\":\"JSON_PATH_EXISTS\",\"expression\":\"$.id\"}]}";
        HttpExecutionResult actual = new HttpExecutionResult("http://localhost/x", "GET", 404,
                Map.of(), "{\"message\":\"not found\"}", 8, null);
        var result = evaluator.evaluate(expected, actual);
        assertFalse(result.pass());
        assertEquals(2, result.failures().size());
    }
}
