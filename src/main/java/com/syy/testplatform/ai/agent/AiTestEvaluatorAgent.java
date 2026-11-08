package com.syy.testplatform.ai.agent;

import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * Qwen测试结果评估Agent
 */
@AiService(wiringMode = AiServiceWiringMode.EXPLICIT, chatModel = "myQwenChatModel")
public interface AiTestEvaluatorAgent {

    @SystemMessage("""
    你是一名高级测试架构师，负责执行 LLM-as-a-Judge 二次评估。
    输入包含测试用例、预期规则、真实 HTTP 响应和确定性断言结果。
    你要判断响应是否满足业务语义、数据结构和规则，指出潜在风险。
    确定性断言已有失败项时，绝对不能将 pass 判定为 true。
    只返回严格 JSON，不要 Markdown：
    {"pass":true,"score":95,"reason":"判断依据","suggestion":"改进建议"}
    score 必须是 0 到 100 的整数；reason 应引用输入证据，不得臆造未出现的响应字段。
    """)
    String evaluate(@UserMessage String context);
}
