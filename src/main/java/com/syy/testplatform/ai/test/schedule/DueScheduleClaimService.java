package com.syy.testplatform.ai.test.schedule;

import com.syy.testplatform.ai.test.entity.TestSchedule;
import com.syy.testplatform.ai.test.repository.TestScheduleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class DueScheduleClaimService {
    private final TestScheduleRepository repository;
    private final ZoneId zoneId;

    public DueScheduleClaimService(TestScheduleRepository repository,
                                   @Value("${app.scheduler.zone:Asia/Shanghai}") String zone) {
        this.repository = repository;
        this.zoneId = ZoneId.of(zone);
    }

    @Transactional
    public List<DueSchedule> claim(int batchSize) {
        LocalDateTime now = LocalDateTime.now(zoneId);
        List<TestSchedule> due = repository.findDueForUpdate(now, PageRequest.of(0, Math.max(1, batchSize)));
        return due.stream().map(schedule -> {
            LocalDateTime claimedAt = LocalDateTime.now(zoneId);
            schedule.setLastRunAt(claimedAt);
            schedule.setNextRunAt(CronExpression.parse(schedule.getCronExpression()).next(claimedAt));
            schedule.setUpdatedAt(claimedAt);
            repository.save(schedule);
            return new DueSchedule(schedule.getId(), schedule.getDocumentId(), schedule.getTaskName(), schedule.getBaseUrl());
        }).toList();
    }
}
