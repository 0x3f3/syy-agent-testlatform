package com.syy.testplatform.ai.test.repository;
import com.syy.testplatform.ai.test.entity.ApiDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ApiDocumentRepository extends JpaRepository<ApiDocument,Long>{ List<ApiDocument> findAllByOrderByImportedAtDesc(); }
