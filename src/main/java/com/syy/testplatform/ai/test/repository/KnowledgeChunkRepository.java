package com.syy.testplatform.ai.test.repository;
import com.syy.testplatform.ai.test.entity.KnowledgeChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk,Long>{
 List<KnowledgeChunk> findBySource(String source);
 void deleteBySource(String source);
}
