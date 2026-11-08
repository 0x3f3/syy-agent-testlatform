package com.syy.testplatform.ai.test.parser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.syy.testplatform.ai.test.entity.ApiDefinition;
import org.springframework.stereotype.Component;
import java.io.InputStream;
import java.util.*;

@Component
public class OpenApiParser {
 private static final Set<String> METHODS=Set.of("GET","POST","PUT","PATCH","DELETE","HEAD","OPTIONS","TRACE");
 private final ObjectMapper mapper;
 private final ObjectMapper yamlMapper;
 public OpenApiParser(ObjectMapper mapper){this.mapper=mapper;this.yamlMapper=new ObjectMapper(new YAMLFactory());}
 public ParsedOpenApi parse(InputStream input)throws Exception{
  byte[] bytes = input.readAllBytes();
  JsonNode root;
  try { root = mapper.readTree(bytes); }
  catch (Exception jsonError) { root = yamlMapper.readTree(bytes); }
  if (root == null) root = yamlMapper.readTree(bytes);
  if(root==null||!root.path("paths").isObject()) throw new IllegalArgumentException("无效的 OpenAPI 文档：缺少 paths 对象");
  String openApiVersion=root.path("openapi").asText(""); String swaggerVersion=root.path("swagger").asText("");
  if(!openApiVersion.startsWith("3.")&&!"2.0".equals(swaggerVersion)) throw new IllegalArgumentException("仅支持 OpenAPI 3.x 或 Swagger 2.0 JSON/YAML 文档");
  String title=root.path("info").path("title").asText("未命名 API 文档");
  String version=root.path("info").path("version").asText("未指定");
  String spec=openApiVersion.isBlank()?"Swagger "+swaggerVersion:"OpenAPI "+openApiVersion;
  String defaultServer=resolveServer(root,"2.0".equals(swaggerVersion));
  List<ApiDefinition> apis=new ArrayList<>();
  Iterator<Map.Entry<String,JsonNode>> paths=root.path("paths").fields();
  while(paths.hasNext()){
   var pathEntry=paths.next(); String path=pathEntry.getKey(); JsonNode pathItem=pathEntry.getValue();
   Iterator<Map.Entry<String,JsonNode>> operations=pathItem.fields();
   while(operations.hasNext()){
    var operationEntry=operations.next(); String method=operationEntry.getKey().toUpperCase(Locale.ROOT);
    if(!METHODS.contains(method)) continue;
    JsonNode op=operationEntry.getValue(); ApiDefinition api=new ApiDefinition();
    api.setPath(path); api.setMethod(method); api.setOperationId(op.path("operationId").asText(""));
    api.setDescription(op.path("summary").asText(op.path("description").asText(api.getOperationId())));
    api.setServerUrl(resolveOperationServer(op,defaultServer)); api.setTags(joinTags(op.path("tags")));
    api.setParametersJson(mergeParameters(pathItem.path("parameters"),op.path("parameters"),root));
    api.setRequestBodySchemaJson(extractRequestSchema(op,"2.0".equals(swaggerVersion),root));
    api.setRequestExampleJson(extractRequestExample(op,"2.0".equals(swaggerVersion)));
    api.setResponsesJson(asJson(resolveRefs(op.path("responses"),root,new HashSet<>())));
    api.setSecurityJson(extractSecurity(op,root));
    api.setRawOperationJson(asJson(resolveRefs(op,root,new HashSet<>()))); apis.add(api);
   }
  }
  if(apis.isEmpty()) throw new IllegalArgumentException("OpenAPI 文档中没有识别到 HTTP 接口");
  return new ParsedOpenApi(title,version,spec,defaultServer,mapper.writeValueAsString(root),apis);
 }
 private String resolveServer(JsonNode root,boolean swagger2){
  if(!swagger2){JsonNode servers=root.path("servers"); if(servers.isArray()&&!servers.isEmpty())return servers.get(0).path("url").asText("");return "";}
  String host=root.path("host").asText(""); if(host.isBlank())return "";
  String scheme="https";JsonNode schemes=root.path("schemes");if(schemes.isArray()&&!schemes.isEmpty())scheme=schemes.get(0).asText("https");
  return scheme+"://"+host+root.path("basePath").asText("");
 }
 private String resolveOperationServer(JsonNode op,String fallback){JsonNode servers=op.path("servers");return servers.isArray()&&!servers.isEmpty()?servers.get(0).path("url").asText(fallback):fallback;}
 private String mergeParameters(JsonNode pathParams,JsonNode opParams,JsonNode root)throws Exception{ArrayNode merged=mapper.createArrayNode();if(pathParams.isArray())pathParams.forEach(p->merged.add(resolveRefs(p,root,new HashSet<>())));if(opParams.isArray())opParams.forEach(p->merged.add(resolveRefs(p,root,new HashSet<>())));return mapper.writeValueAsString(merged);}
 private String extractRequestSchema(JsonNode op,boolean swagger2,JsonNode root)throws Exception{
  if(swagger2){for(JsonNode p:op.path("parameters"))if("body".equals(p.path("in").asText()))return asJson(resolveRefs(p.path("schema"),root,new HashSet<>()));return "";}
  JsonNode content=op.path("requestBody").path("content");JsonNode media=content.path("application/json");
  if(media.isMissingNode()&&content.isObject()&&content.fieldNames().hasNext())media=content.elements().next();return asJson(resolveRefs(media.path("schema"),root,new HashSet<>()));
 }
 private String extractRequestExample(JsonNode op,boolean swagger2)throws Exception{
  if(swagger2){for(JsonNode p:op.path("parameters"))if("body".equals(p.path("in").asText())&&p.has("x-example"))return asJson(p.path("x-example"));return "";}
  JsonNode content=op.path("requestBody").path("content");JsonNode media=content.path("application/json");if(media.isMissingNode()&&content.isObject()&&content.fieldNames().hasNext())media=content.elements().next();
  if(media.has("example"))return asJson(media.path("example"));JsonNode examples=media.path("examples");if(examples.isObject()&&examples.fieldNames().hasNext())return asJson(examples.elements().next().path("value"));return "";
 }
 private String joinTags(JsonNode tags){if(!tags.isArray())return "";List<String> values=new ArrayList<>();tags.forEach(t->values.add(t.asText()));return String.join(",",values);}


 private String extractSecurity(JsonNode op,JsonNode root)throws Exception{
  com.fasterxml.jackson.databind.node.ObjectNode security=mapper.createObjectNode();
  JsonNode requirements=op.path("security").isMissingNode()?root.path("security"):op.path("security");
  JsonNode schemes=root.path("components").path("securitySchemes");
  if(schemes.isMissingNode())schemes=root.path("securityDefinitions");
  if(requirements.isMissingNode())security.putArray("requirements");else security.set("requirements",resolveRefs(requirements,root,new HashSet<>()));
  security.set("schemes",schemes.isMissingNode()?mapper.createObjectNode():schemes.deepCopy());
  return mapper.writeValueAsString(security);
 }
 private JsonNode resolveRefs(JsonNode node,JsonNode root,Set<String> visited){
  if(node==null||node.isMissingNode()||node.isNull())return node;
  if(node.isObject()){
   JsonNode refNode=node.get("$ref");
   if(refNode!=null&&refNode.isTextual()){
    String ref=refNode.asText();
    if(ref.startsWith("#/")&&!visited.contains(ref)){
     JsonNode target=root.at(ref.substring(1));
     if(!target.isMissingNode()){
      Set<String> next=new HashSet<>(visited);next.add(ref);
      JsonNode resolved=resolveRefs(target,root,next);
      if(resolved.isObject()){
       com.fasterxml.jackson.databind.node.ObjectNode merged=((com.fasterxml.jackson.databind.node.ObjectNode)resolved).deepCopy();
       node.fields().forEachRemaining(entry->{if(!"$ref".equals(entry.getKey()))merged.set(entry.getKey(),resolveRefs(entry.getValue(),root,next));});
       return merged;
      }
      return resolved;
     }
    }
   }
   com.fasterxml.jackson.databind.node.ObjectNode copy=mapper.createObjectNode();
   node.fields().forEachRemaining(entry->copy.set(entry.getKey(),resolveRefs(entry.getValue(),root,visited)));
   return copy;
  }
  if(node.isArray()){
   ArrayNode copy=mapper.createArrayNode();node.forEach(child->copy.add(resolveRefs(child,root,visited)));return copy;
  }
  return node.deepCopy();
 }
 private String asJson(JsonNode node)throws Exception{return node==null||node.isMissingNode()||node.isNull()?"":mapper.writeValueAsString(node);}
}
