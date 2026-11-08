package com.syy.testplatform.ai.test.rag;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syy.testplatform.ai.test.entity.KnowledgeChunk;
import com.syy.testplatform.ai.test.repository.KnowledgeChunkRepository;
import com.syy.testplatform.ai.test.service.RedisPlatformCache;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class KnowledgeBaseService {
 private static final Logger log=LoggerFactory.getLogger(KnowledgeBaseService.class);
 @Resource(name="qwenEmbeddingModel") private EmbeddingModel embeddingModel;
 private final KnowledgeChunkRepository repository;
 private final ObjectMapper mapper;
 private final RedisPlatformCache cache;
 public KnowledgeBaseService(KnowledgeChunkRepository repository,ObjectMapper mapper,RedisPlatformCache cache){this.repository=repository;this.mapper=mapper;this.cache=cache;}

 public void indexApiDocument(String source,String text){
  indexText(source,text);
  indexBuiltInDocs();
 }

 public void indexBuiltInDocs(){
  for(String file:List.of("docs/api-testing-principles.md","docs/openapi-test-design.md")){
   String source="builtin:"+file;
   if(!repository.findBySource(source).isEmpty()) continue;
   try{ClassPathResource resource=new ClassPathResource(file);if(resource.exists())indexText(source,new String(resource.getInputStream().readAllBytes(),StandardCharsets.UTF_8));}
   catch(Exception ex){log.warn("Unable to index built-in RAG document {}: {}",file,ex.getMessage());}
  }
 }

 public void indexText(String source,String text){
  if(text==null||text.isBlank())return;
  repository.deleteBySource(source);
  cache.invalidateRagSearch();
  text=redact(text);
  List<String> chunks=split(text,800);
  for(String chunk:chunks){
   try{
    float[] vector=embeddingModel.embed(chunk).content().vector();
    KnowledgeChunk entity=new KnowledgeChunk();entity.setSource(source);entity.setContent(chunk);
    entity.setEmbeddingJson(mapper.writeValueAsString(vector));repository.save(entity);
   }catch(Exception ex){log.warn("RAG embedding failed for source {}: {}",source,ex.getMessage());throw new IllegalStateException("RAG 向量化失败，请检查 DASHSCOPE_API_KEY 和 Embedding 模型配置",ex);}
  }
 }

 public String search(String query,int topK){
  var cached = cache.getRagSearch(query, topK);
  if (cached.isPresent()) return cached.get();
  List<KnowledgeChunk> chunks=repository.findAll();if(chunks.isEmpty())throw new IllegalStateException("RAG 知识库为空，请先成功建立 OpenAPI 和内置测试知识向量索引");
  float[] queryVector=embeddingModel.embed(query).content().vector();
  List<ScoredChunk> scored=new ArrayList<>();
  for(KnowledgeChunk chunk:chunks){
   try{float[] vector=mapper.readValue(chunk.getEmbeddingJson(),float[].class);scored.add(new ScoredChunk(chunk,cosine(queryVector,vector)));}
   catch(Exception ex){log.debug("Skip malformed RAG vector id={}",chunk.getId());}
  }
  scored.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());
  StringBuilder result=new StringBuilder();
  scored.stream().limit(Math.max(1,topK)).forEach(s->result.append("[来源: ").append(s.chunk().getSource()).append(", 相似度: ").append(String.format(Locale.ROOT,"%.3f",s.score())).append("]\n").append(s.chunk().getContent()).append("\n\n"));
  if(result.length()==0)throw new IllegalStateException("RAG 向量检索没有返回有效知识块");
  String value = result.toString();
  cache.putRagSearch(query, topK, value);
  return value;
 }


 public RagSearchDiagnostics searchDebug(String query, int topK) {
  if (query == null || query.isBlank()) throw new IllegalArgumentException("RAG 检索 query 不能为空");
  int limit = Math.max(1, Math.min(topK, 20));
  List<KnowledgeChunk> chunks = repository.findAll();
  if (chunks.isEmpty()) {
   return new RagSearchDiagnostics(query, limit, 0, 0, true, List.of());
  }
  float[] queryVector = embeddingModel.embed(query).content().vector();
  List<ScoredChunk> scored = new ArrayList<>();
  for (KnowledgeChunk chunk : chunks) {
   try {
    float[] vector = mapper.readValue(chunk.getEmbeddingJson(), float[].class);
    scored.add(new ScoredChunk(chunk, cosine(queryVector, vector)));
   } catch (Exception ex) {
    log.debug("Skip malformed RAG vector id={}", chunk.getId());
   }
  }
  scored.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());
  List<RagSearchDiagnostics.Hit> hits = scored.stream().limit(limit)
    .map(item -> new RagSearchDiagnostics.Hit(
      item.chunk().getId(), item.chunk().getSource(),
      Math.round(item.score() * 10000.0) / 10000.0,
      item.chunk().getContent()))
    .toList();
  return new RagSearchDiagnostics(query, limit, chunks.size(), hits.size(), true, hits);
 }

 public int chunkCount(){return (int)repository.count();}
 private String redact(String text){
  String safe=text.replaceAll("(?i)(\"?[^\"\\s]*(?:password|token|secret|authorization|api[_-]?key)[^\"\\s]*\"?\\s*:\\s*\")[^\"]*(\")","$1***$2");
  return safe.replaceAll("(?i)(password|token|secret|api[_-]?key)=([^&\\s]+)","$1=***");
 }
 private List<String> split(String text,int max){
  List<String> result=new ArrayList<>();String normalized=text.replace("\r","\n");
  for(String paragraph:normalized.split("\\n\\s*\\n")){
   String p=paragraph.trim();if(p.isEmpty())continue;
   for(int start=0;start<p.length();start+=max){int end=Math.min(p.length(),start+max);result.add(p.substring(start,end));}
  }
  if(result.isEmpty())result.add(text.substring(0,Math.min(text.length(),max)));
  return result;
 }
 private double cosine(float[] a,float[] b){if(a.length!=b.length)return 0;double dot=0,na=0,nb=0;for(int i=0;i<a.length;i++){dot+=a[i]*b[i];na+=a[i]*a[i];nb+=b[i]*b[i];}return na==0||nb==0?0:dot/(Math.sqrt(na)*Math.sqrt(nb));}
 private record ScoredChunk(KnowledgeChunk chunk,double score){}
}
