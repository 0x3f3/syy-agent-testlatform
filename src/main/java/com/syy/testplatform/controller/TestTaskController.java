package com.syy.testplatform.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.syy.testplatform.ai.test.entity.TestExecution;
import com.syy.testplatform.ai.test.entity.TestReport;
import com.syy.testplatform.ai.test.entity.TestTask;
import com.syy.testplatform.ai.test.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "异步测试任务")
@RestController
@RequestMapping("/test/tasks")
@RequiredArgsConstructor
public class TestTaskController {
    private final TestTaskService service;

    @PostMapping
    public ResponseEntity<TaskSubmissionResponse> submit(@RequestBody CreateTestTaskRequest request) {
        return ResponseEntity.accepted().body(service.submit(request));
    }

    @GetMapping
    public List<TestTask> list() { return service.listTasks(); }

    @GetMapping("/{id}")
    public TestTask detail(@PathVariable Long id) { return service.getTask(id); }

    @GetMapping("/{id}/progress")
    public TaskProgressResponse progress(@PathVariable Long id) { return service.getProgress(id); }

    @GetMapping("/{id}/executions")
    public List<TestExecution> executions(@PathVariable Long id) { return service.executions(id); }

    @GetMapping("/{id}/report")
    public TestReport report(@PathVariable Long id) {
        return service.reportByTask(id).orElseThrow(() -> new IllegalArgumentException("该任务尚未生成报告"));
    }
}
