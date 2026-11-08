package com.syy.testplatform.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.syy.testplatform.ai.test.schedule.CreateTestScheduleRequest;
import com.syy.testplatform.ai.test.schedule.TestScheduleResponse;
import com.syy.testplatform.ai.test.schedule.TestScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "定时任务调度")
@RestController
@RequestMapping("/test/schedules")
@RequiredArgsConstructor
public class TestScheduleController {
    private final TestScheduleService service;

    @PostMapping
    public TestScheduleResponse create(@RequestBody CreateTestScheduleRequest request) { return service.create(request); }

    @GetMapping
    public List<TestScheduleResponse> list() { return service.list(); }

    @PatchMapping("/{id}/enabled")
    public TestScheduleResponse enabled(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        return service.setEnabled(id, Boolean.TRUE.equals(body.get("enabled")));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) { service.delete(id); }
}
