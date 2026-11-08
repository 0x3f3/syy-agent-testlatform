package com.syy.testplatform.ai.test.evaluation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syy.testplatform.ai.agent.AiTestEvaluatorAgent;
import org.springframework.stereotype.Service;
@Service
public class LlmEvaluationService {
 private final AiTestEvaluatorAgent agent;private final ObjectMapper mapper;
 public LlmEvaluationService(AiTestEvaluatorAgent agent,ObjectMapper mapper){this.agent=agent;this.mapper=mapper;}
 public JudgeResult evaluate(String caseName,String expected,String actual,AssertionEvaluator.DeterministicResult deterministic){
  String context="用例名称："+caseName+"\n预期规则："+expected+"\n实际 HTTP 响应："+limit(actual,12000)+"\n确定性断言结果："+(deterministic.pass()?"通过":"失败")+"\n确定性失败项："+String.join("；",deterministic.failures())+"\n请进行语义层面的二次评估。确定性断言失败时，不得将最终 pass 改为 true。输出 JSON：{\"pass\":true,\"score\":95,\"reason\":\"...\",\"suggestion\":\"...\"}";
  try{String output=agent.evaluate(context);JsonNode node=parse(output);boolean pass=node.path("pass").asBoolean(deterministic.pass());int score=Math.max(0,Math.min(100,node.path("score").asInt(deterministic.score())));String reason=node.path("reason").asText("LLM 已完成语义评估");String suggestion=node.path("suggestion").asText("");return new JudgeResult(deterministic.pass()&&pass,Math.min(score,deterministic.score()),reason,suggestion,true,output);}
  catch(Exception ex){return new JudgeResult(deterministic.pass(),deterministic.score(),"LLM Judge 调用失败，已采用确定性断言结果："+safe(ex),"检查 DashScope Key、模型权限和网络连接",false,"");}
 }
 private JsonNode parse(String text)throws Exception{try{return mapper.readTree(text);}catch(Exception ignored){}int start=text.indexOf('{');int end=text.lastIndexOf('}');if(start>=0&&end>start)return mapper.readTree(text.substring(start,end+1));throw new IllegalArgumentException("LLM Judge 未返回 JSON");}
 private String limit(String value,int max){if(value==null)return "";return value.length()>max?value.substring(0,max)+"...(truncated)":value;}
 private String safe(Exception ex){String m=ex.getMessage();return m==null?ex.getClass().getSimpleName():m.substring(0,Math.min(500,m.length()));}
 public record JudgeResult(boolean pass,int score,String reason,String suggestion,boolean available,String raw){}
}
