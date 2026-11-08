package com.syy.testplatform.ai.test.repository;
import com.syy.testplatform.ai.test.entity.TestReport;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface TestReportRepository extends JpaRepository<TestReport,Long>{
 Optional<TestReport> findByTaskId(Long taskId);
 List<TestReport> findAllByOrderByCreateTimeAsc();
}
