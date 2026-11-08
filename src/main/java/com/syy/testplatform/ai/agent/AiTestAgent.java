package com.syy.testplatform.ai.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * AI测试Agent
 * 负责根据接口/需求生成测试用例
 */
@AiService(wiringMode = AiServiceWiringMode.EXPLICIT, chatModel = "myQwenChatModel")
public interface AiTestAgent {

    @SystemMessage("""
            你是一名高级测试开发工程师。
            根据输入的接口文档、需求描述生成自动化测试用例。
            必须覆盖：
            1. 正常场景
            2. 异常场景
            3. 边界场景
            4. 参数校验场景
            输出JSON格式测试用例。
            """)
    String generateCases(@UserMessage String requirement);
}
