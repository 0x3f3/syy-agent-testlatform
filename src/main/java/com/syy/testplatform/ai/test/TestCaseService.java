package com.syy.testplatform.ai.test;

import com.syy.testplatform.ai.agent.AiTestAgent;
import com.syy.testplatform.ai.agent.AiTestEvaluatorAgent;
import org.springframework.stereotype.Service;

@Service
public class TestCaseService {

    private final AiTestAgent aiTestAgent;
    private final AiTestEvaluatorAgent evaluatorAgent;

    public TestCaseService(AiTestAgent aiTestAgent, AiTestEvaluatorAgent evaluatorAgent) {
        this.aiTestAgent = aiTestAgent;
        this.evaluatorAgent = evaluatorAgent;
    }

    public String generate(String requirement) {
        return aiTestAgent.generateCases(requirement);
    }

    public String evaluate(String result) {
        return evaluatorAgent.evaluate(result);
    }
}
