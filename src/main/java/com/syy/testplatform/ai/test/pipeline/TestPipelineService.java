package com.syy.testplatform.ai.test.pipeline;

import org.springframework.stereotype.Service;

/**
 * Swagger自动化测试流水线
 */
@Service
public class TestPipelineService {

    public String run(Long swaggerId){

        /*
        Swagger JSON
             ↓
        OpenAPI解析
             ↓
        AI生成100条测试
             ↓
        HttpExecutor执行
             ↓
        Evaluator Agent评分
             ↓
        Markdown报告
        */

        return "PIPELINE_STARTED:"+swaggerId;
    }
}
