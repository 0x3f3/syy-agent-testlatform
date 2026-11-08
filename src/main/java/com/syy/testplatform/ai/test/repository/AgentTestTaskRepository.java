package com.syy.testplatform.ai.test.repository;
import com.syy.testplatform.ai.test.entity.TestTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AgentTestTaskRepository extends JpaRepository<TestTask,Long>{
 List<TestTask> findAllByOrderByCreatedAtDesc();
 List<TestTask> findByDocumentIdOrderByCreatedAtDesc(Long documentId);
}
