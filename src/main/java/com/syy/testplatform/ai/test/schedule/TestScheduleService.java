package com.syy.testplatform.ai.test.schedule;

import com.syy.testplatform.ai.test.entity.ApiDocument;
import com.syy.testplatform.ai.test.entity.TestSchedule;
import com.syy.testplatform.ai.test.repository.TestScheduleRepository;
import com.syy.testplatform.ai.test.service.CreateTestTaskRequest;
import com.syy.testplatform.ai.test.service.TaskSubmissionResponse;
import com.syy.testplatform.ai.test.service.TestPlatformService;
import com.syy.testplatform.ai.test.service.TestTaskService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class TestScheduleService {
    private final TestScheduleRepository repository;
    private final TestPlatformService platformService;
    private final TestTaskService taskService;
    private final DueScheduleClaimService claimService;
    private final ZoneId zoneId;

    public TestScheduleService(TestScheduleRepository repository, TestPlatformService platformService,
                               TestTaskService taskService, DueScheduleClaimService claimService,
                               @Value("${app.scheduler.zone:Asia/Shanghai}") String zone) {
        this.repository = repository;
        this.platformService = platformService;
        this.taskService = taskService;
        this.claimService = claimService;
        this.zoneId = ZoneId.of(zone);
    }

    public TestScheduleResponse create(CreateTestScheduleRequest request) {
        if (request == null || request.documentId() == null) throw new IllegalArgumentException("documentId 不能为空");
        if (request.cronExpression() == null || request.cronExpression().isBlank()) throw new IllegalArgumentException("cronExpression 不能为空");
        ApiDocument document = platformService.getDocument(request.documentId());
        CronExpression cron = parseCron(request.cronExpression());
        LocalDateTime now = LocalDateTime.now(zoneId);
        LocalDateTime next = cron.next(now);
        if (next == null) throw new IllegalArgumentException("Cron 表达式没有下一次执行时间");

        TestSchedule schedule = new TestSchedule();
        schedule.setScheduleName(limit(blankTo(request.scheduleName(), document.getTitle() + " 定时回归"), 255));
        schedule.setDocumentId(document.getId());
        schedule.setTaskName(limit(blankTo(request.taskName(), document.getTitle() + " 定时测试"), 255));
        schedule.setBaseUrl(limit(blankTo(request.baseUrl(), document.getServerUrl()), 1024));
        schedule.setCronExpression(request.cronExpression().trim());
        schedule.setEnabled(request.enabled() == null || request.enabled());
        schedule.setNextRunAt(next);
        schedule.setCreatedAt(now);
        schedule.setUpdatedAt(now);
        return toResponse(repository.save(schedule));
    }

    public List<TestScheduleResponse> list() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public TestScheduleResponse setEnabled(Long id, boolean enabled) {
        TestSchedule schedule = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("定时任务不存在：" + id));
        schedule.setEnabled(enabled);
        schedule.setUpdatedAt(LocalDateTime.now(zoneId));
        if (enabled && (schedule.getNextRunAt() == null || !schedule.getNextRunAt().isAfter(LocalDateTime.now(zoneId)))) {
            schedule.setNextRunAt(parseCron(schedule.getCronExpression()).next(LocalDateTime.now(zoneId)));
        }
        return toResponse(repository.save(schedule));
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) throw new IllegalArgumentException("定时任务不存在：" + id);
        repository.deleteById(id);
    }

    public void dispatchDueSchedules() {
        for (DueSchedule due : claimService.claim(20)) {
            try {
                TaskSubmissionResponse submitted = taskService.submit(
                        new CreateTestTaskRequest(due.documentId(), due.taskName(), due.baseUrl(), null));
                repository.findById(due.scheduleId()).ifPresent(schedule -> {
                    schedule.setLastTaskId(submitted.taskId());
                    repository.save(schedule);
                });
            } catch (Exception ex) {
                // Retry the failed dispatch shortly; one bad schedule must not block other due plans.
                repository.findById(due.scheduleId()).ifPresent(schedule -> {
                    schedule.setNextRunAt(LocalDateTime.now(zoneId).plusMinutes(1));
                    schedule.setUpdatedAt(LocalDateTime.now(zoneId));
                    repository.save(schedule);
                });
                org.slf4j.LoggerFactory.getLogger(getClass()).error("定时任务触发失败，scheduleId={}，已安排 1 分钟后重试",
                        due.scheduleId(), ex);
            }
        }
    }

    private CronExpression parseCron(String value) {
        try { return CronExpression.parse(value.trim()); }
        catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Cron 表达式无效。请使用 Spring 六段格式，例如：0 0/30 * * * *", ex);
        }
    }

    private TestScheduleResponse toResponse(TestSchedule s) {
        return new TestScheduleResponse(s.getId(), s.getScheduleName(), s.getDocumentId(), s.getTaskName(),
                s.getBaseUrl(), s.getCronExpression(), s.getEnabled(), s.getNextRunAt(), s.getLastRunAt(), s.getLastTaskId());
    }

    private String blankTo(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private String limit(String value, int max) { return value == null ? null : value.substring(0, Math.min(value.length(), max)); }
}
