package com.syy.testplatform.ai.test.evaluation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syy.testplatform.test.executor.HttpExecutionResult;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.*;
@Service
public class AssertionEvaluator {
 private final ObjectMapper mapper;
 public AssertionEvaluator(ObjectMapper mapper){this.mapper=mapper;}
 public DeterministicResult evaluate(String expectedJson,HttpExecutionResult actual){
  List<String> failures=new ArrayList<>();JsonNode expected;
  try{expected=expectedJson==null||expectedJson.isBlank()?mapper.createObjectNode():mapper.readTree(expectedJson);}catch(Exception e){return new DeterministicResult(false,0,List.of("预期结果不是有效 JSON"));}
  if(actual.transportError()!=null&&!actual.transportError().isBlank())return new DeterministicResult(false,0,List.of("HTTP 请求失败："+actual.transportError()));
  int expectedStatus=expected.path("statusCode").asInt(200);if(actual.statusCode()!=expectedStatus)failures.add("HTTP 状态码不匹配，期望 "+expectedStatus+"，实际 "+actual.statusCode());
  JsonNode assertions=expected.path("assertions");if(assertions.isArray())for(JsonNode assertion:assertions){String type=assertion.path("type").asText("STATUS_CODE").toUpperCase(Locale.ROOT);String expression=assertion.path("expression").asText("");JsonNode expectedValue=assertion.path("expectedValue");
   try{switch(type){
    case "STATUS_CODE"->{int code=assertion.path("expectedValue").asInt(expectedStatus);if(actual.statusCode()!=code)failures.add("STATUS_CODE 断言失败：期望 "+code+"，实际 "+actual.statusCode());}
    case "JSON_PATH_EXISTS"->{JsonNode value=readPath(actual.body(),expression);if(value==null||value.isMissingNode()||value.isNull())failures.add("JSON_PATH_EXISTS 断言失败："+expression);}
    case "JSON_PATH_EQUALS"->{JsonNode value=readPath(actual.body(),expression);JsonNode wanted=expectedValue.isMissingNode()?mapper.getNodeFactory().nullNode():expectedValue;if(value==null||!value.equals(wanted))failures.add("JSON_PATH_EQUALS 断言失败："+expression+"，期望 "+wanted+"，实际 "+value);}
    case "JSON_PATH_CONTAINS"->{JsonNode value=readPath(actual.body(),expression);if(value==null||!value.asText().contains(expectedValue.asText()))failures.add("JSON_PATH_CONTAINS 断言失败："+expression);}
    case "BODY_CONTAINS"->{String wanted=assertion.path("expectedValue").asText("");if(actual.body()==null||!actual.body().contains(wanted))failures.add("BODY_CONTAINS 断言失败："+wanted);}
    case "HEADER_EQUALS"->{String name=assertion.path("header").asText("");String wanted=expectedValue.asText("");String found=actual.headers().entrySet().stream().filter(e->e.getKey().equalsIgnoreCase(name)).flatMap(e->e.getValue().stream()).findFirst().orElse("");if(!found.equalsIgnoreCase(wanted))failures.add("HEADER_EQUALS 断言失败："+name);}
    default->failures.add("不支持的断言类型："+type);
   }}catch(Exception ex){failures.add(type+" 断言执行异常："+ex.getMessage());}
  }
  int score=failures.isEmpty()?100:Math.max(0,100-failures.size()*25);return new DeterministicResult(failures.isEmpty(),score,failures);
 }
 private JsonNode readPath(String json,String expression)throws Exception{
  if(json==null||json.isBlank())return null;JsonNode current=mapper.readTree(json);String path=expression==null?"":expression.trim();if(path.equals("$")||path.isBlank())return current;if(path.startsWith("$"))path=path.substring(1);
  Matcher matcher=Pattern.compile("(?:\\.([A-Za-z0-9_\\-$]+)|\\[(\\d+)\\])").matcher(path);int consumed=0;
  while(matcher.find()){if(matcher.start()!=consumed)return null;consumed=matcher.end();if(matcher.group(1)!=null)current=current==null?null:current.path(matcher.group(1));else current=current==null?null:current.path(Integer.parseInt(matcher.group(2)));if(current==null||current.isMissingNode())return null;}
  return consumed==path.length()?current:null;
 }
 public record DeterministicResult(boolean pass,int score,List<String> failures){}
}
