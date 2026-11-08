package com.syy.testplatform.test.executor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.syy.testplatform.ai.test.entity.ApiDefinition;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.core.env.Environment;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class HttpExecutor {
 private static final Pattern ENV_PATTERN=Pattern.compile("\\$\\{env:([A-Za-z_][A-Za-z0-9_]*)\\}");
 private final RestTemplate restTemplate;private final ObjectMapper mapper;private final Environment environment;
 public HttpExecutor(RestTemplateBuilder builder,ObjectMapper mapper,Environment environment,
                    @Value("${app.http.connect-timeout-ms:5000}") long connectTimeoutMs,
                    @Value("${app.http.read-timeout-ms:15000}") long readTimeoutMs){
  this.restTemplate=builder.setConnectTimeout(Duration.ofMillis(Math.max(100,connectTimeoutMs)))
          .setReadTimeout(Duration.ofMillis(Math.max(100,readTimeoutMs))).build();
  this.mapper=mapper;this.environment=environment;
 }
 public HttpExecutionResult execute(ApiDefinition api,String baseUrl,String requestJson){
  long started=System.nanoTime();String method=api.getMethod();String url="";
  try{
   if(baseUrl==null||baseUrl.isBlank())throw new IllegalArgumentException("未配置目标服务 Base URL");
   JsonNode request=requestJson==null||requestJson.isBlank()?mapper.createObjectNode():mapper.readTree(requestJson);
   request=resolveEnvironment(request);
   Map<String,Object> pathParams=toMap(request.path("pathParams"));Map<String,Object> queryParams=toMap(request.path("queryParams"));
   String base=baseUrl.replaceAll("/+$","");UriComponentsBuilder uriBuilder=UriComponentsBuilder.fromUriString(base).path(api.getPath());
   queryParams.forEach((k,v)->{if(v instanceof Collection<?> values)values.forEach(item->uriBuilder.queryParam(k,item));else uriBuilder.queryParam(k,v);});
   URI target=uriBuilder.buildAndExpand(pathParams).encode().toUri();url=target.toString();
   HttpHeaders headers=new HttpHeaders();headers.setAccept(List.of(MediaType.APPLICATION_JSON));
   JsonNode headerNode=request.path("headers");if(headerNode.isObject()){Iterator<Map.Entry<String,JsonNode>> it=headerNode.fields();while(it.hasNext()){var e=it.next();if(!e.getValue().isNull())headers.set(e.getKey(),e.getValue().asText());}}
   JsonNode bodyNode=request.path("body");String body=(bodyNode.isMissingNode()||bodyNode.isNull())?null:mapper.writeValueAsString(bodyNode);
   if(body!=null&&!headers.containsKey(HttpHeaders.CONTENT_TYPE))headers.setContentType(MediaType.APPLICATION_JSON);
   HttpMethod httpMethod=HttpMethod.valueOf(method.toUpperCase(Locale.ROOT));HttpEntity<String> entity=new HttpEntity<>(body,headers);
   ResponseEntity<String> response=restTemplate.exchange(target,httpMethod,entity,String.class);
   return new HttpExecutionResult(url,method,response.getStatusCode().value(),toHeaderMap(response.getHeaders()),response.getBody(),elapsed(started),null);
  }catch(HttpStatusCodeException ex){return new HttpExecutionResult(url,method,ex.getStatusCode().value(),toHeaderMap(ex.getResponseHeaders()),ex.getResponseBodyAsString(),elapsed(started),null);}
  catch(Exception ex){return new HttpExecutionResult(url,method,0,Map.of(),"",elapsed(started),safeMessage(ex));}
 }

 private JsonNode resolveEnvironment(JsonNode node){
  if(node==null||node.isNull())return node;
  if(node.isTextual()){
   String value=node.asText();Matcher matcher=ENV_PATTERN.matcher(value);StringBuffer result=new StringBuffer();
   while(matcher.find()){
    String name=matcher.group(1);String secret=environment.getProperty(name);
    if(secret==null||secret.isBlank())secret=System.getenv(name);
    if(secret==null||secret.isBlank())secret=System.getProperty(name);
    if(secret==null||secret.isBlank())throw new IllegalArgumentException("测试用例引用了未配置的环境变量："+name);
    matcher.appendReplacement(result,Matcher.quoteReplacement(secret));
   }
   matcher.appendTail(result);return TextNode.valueOf(result.toString());
  }
  if(node.isObject()){
   ObjectNode copy=mapper.createObjectNode();node.fields().forEachRemaining(entry->copy.set(entry.getKey(),resolveEnvironment(entry.getValue())));return copy;
  }
  if(node.isArray()){
   ArrayNode copy=mapper.createArrayNode();node.forEach(value->copy.add(resolveEnvironment(value)));return copy;
  }
  return node.deepCopy();
 }
 private Map<String,Object> toMap(JsonNode node){Map<String,Object> result=new LinkedHashMap<>();if(node!=null&&node.isObject()){Iterator<Map.Entry<String,JsonNode>> it=node.fields();while(it.hasNext()){var e=it.next();JsonNode v=e.getValue();if(v.isArray()){List<String> list=new ArrayList<>();v.forEach(x->list.add(x.asText()));result.put(e.getKey(),list);}else if(v.isNumber())result.put(e.getKey(),v.numberValue());else if(v.isBoolean())result.put(e.getKey(),v.booleanValue());else if(!v.isNull())result.put(e.getKey(),v.asText());}}return result;}
 private Map<String,List<String>> toHeaderMap(HttpHeaders headers){Map<String,List<String>> result=new LinkedHashMap<>();if(headers!=null)headers.forEach((k,v)->result.put(k,new ArrayList<>(v)));return result;}
 private long elapsed(long start){return Duration.ofNanos(System.nanoTime()-start).toMillis();}
 private String safeMessage(Exception ex){String m=ex.getMessage();if(m==null||m.isBlank())return ex.getClass().getSimpleName();m=m.replaceAll("(?i)(token|access_token|api[_-]?key|secret|password)=([^&\\s]+)","$1=***");return m.length()>1000?m.substring(0,1000):m;}
}
