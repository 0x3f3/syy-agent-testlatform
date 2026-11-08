package com.syy.testplatform.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.syy.testplatform.ai.test.report.TestReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@Tag(name = "测试报告与质量分析")
@RestController @RequestMapping("/test/report") @RequiredArgsConstructor
public class TestReportController {
 private final TestReportService service;
 @GetMapping("/trend") public Map<String,Object> trend(){return service.trend();}
 @GetMapping("/quality") public Map<String,Object> quality(){return service.quality();}
 @GetMapping("/summary") public Map<String,Object> summary(){return service.summary();}
 @GetMapping("/{id}") public Object detail(@PathVariable Long id){return service.report(id).orElseThrow(()->new IllegalArgumentException("测试报告不存在"));}
}
