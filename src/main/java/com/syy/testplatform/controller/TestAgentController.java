package com.syy.testplatform.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.syy.testplatform.ai.test.TestCaseService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * AI测试助手接口
 */
@Tag(name = "Agent 智能测试")
@RestController
@RequestMapping("/test-agent")
public class TestAgentController {

    @Resource
    private TestCaseService service;

    @PostMapping("/generate")
    public String generate(@RequestBody String requirement){
        return service.generate(requirement);
    }

    @PostMapping("/evaluate")
    public String evaluate(@RequestBody String result){
        return service.evaluate(result);
    }
}
