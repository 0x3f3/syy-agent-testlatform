package com.syy.testplatform.ai.test.evaluation;

import lombok.Data;
import org.springframework.stereotype.Service;

@Service
public class AgentEvaluator {
 public EvaluateResult evaluate(String actual,String expected,String rule){
  int score=actual!=null && actual.contains(expected)?100:60;
  return new EvaluateResult(score,score>=60,"AI规则:"+rule);
 }
 @Data
 public static class EvaluateResult{
  private final int score; private final boolean pass; private final String reason;
 }
}
