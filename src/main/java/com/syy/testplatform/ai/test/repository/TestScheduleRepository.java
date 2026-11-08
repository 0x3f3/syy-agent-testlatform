package com.syy.testplatform.ai.test.repository;

import com.syy.testplatform.ai.test.entity.TestSchedule;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TestScheduleRepository extends JpaRepository<TestSchedule, Long> {
    List<TestSchedule> findAllByOrderByCreatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TestSchedule s where s.enabled = true and s.nextRunAt <= :now order by s.nextRunAt asc")
    List<TestSchedule> findDueForUpdate(@Param("now") LocalDateTime now, Pageable pageable);
}
