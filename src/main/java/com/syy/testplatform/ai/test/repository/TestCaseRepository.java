package com.syy.testplatform.ai.test.repository;
import com.syy.testplatform.ai.test.entity.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface TestCaseRepository extends JpaRepository<TestCase,Long>{
 List<TestCase> findByApiIdOrderByIdAsc(Long apiId);
 List<TestCase> findByApiIdAndEnabledTrueOrderByIdAsc(Long apiId);
 List<TestCase> findByApiIdInOrderByIdAsc(List<Long> apiIds);
 long countByApiIdIn(List<Long> apiIds);
 List<TestCase> findByApiIdInAndEnabledTrueOrderByIdAsc(List<Long> apiIds);
}
