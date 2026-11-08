package com.syy.testplatform.ai.test.repository;
import com.syy.testplatform.ai.test.entity.TestExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface TestExecutionRepository extends JpaRepository<TestExecution,Long>{
 List<TestExecution> findByTaskIdOrderByExecuteTimeAsc(Long taskId);
 List<TestExecution> findAllByOrderByExecuteTimeDesc();
}
