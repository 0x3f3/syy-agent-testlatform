package com.syy.testplatform.ai.test.repository;
import com.syy.testplatform.ai.test.entity.ApiDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
public interface ApiDefinitionRepository extends JpaRepository<ApiDefinition,Long>{
 List<ApiDefinition> findByDocumentIdOrderByIdAsc(Long documentId);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select a from ApiDefinition a where a.documentId = :documentId order by a.id asc")
 List<ApiDefinition> lockByDocumentId(@Param("documentId") Long documentId);
 List<ApiDefinition> findByIdIn(List<Long> ids);
 List<ApiDefinition> findByDocumentId(Long documentId);
}
