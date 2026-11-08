package com.syy.testplatform.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.syy.testplatform.ai.test.service.CreateTestTaskRequest;
import com.syy.testplatform.ai.test.service.TaskSubmissionResponse;
import com.syy.testplatform.ai.test.service.TestTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

@Tag(name = "自动化测试流水线")
@RestController
@RequestMapping("/test/pipeline")
@RequiredArgsConstructor
public class TestPipelineController {
    private final TestTaskService service;

    @PostMapping("/{documentId}/run")
    public ResponseEntity<TaskSubmissionResponse> run(@PathVariable Long documentId,
                                                       @RequestParam(required = false) String baseUrl) {
        return ResponseEntity.accepted().body(
                service.submit(new CreateTestTaskRequest(documentId, "Swagger 自动化测试任务", baseUrl, null)));
    }
}
