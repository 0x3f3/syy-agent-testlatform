package com.syy.testplatform.ai.test.task;

import com.syy.testplatform.ai.test.schedule.TestScheduleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TestTaskScheduler {
    private static final Logger log = LoggerFactory.getLogger(TestTaskScheduler.class);
    private final TestScheduleService scheduleService;
    private final boolean enabled;

    public TestTaskScheduler(TestScheduleService scheduleService,
                             @Value("${app.scheduler.enabled:true}") boolean enabled) {
        this.scheduleService = scheduleService;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${app.scheduler.poll-interval-ms:15000}")
    public void executeDueTasks() {
        if (!enabled) return;
        try { scheduleService.dispatchDueSchedules(); }
        catch (Exception ex) { log.error("扫描定时测试任务失败", ex); }
    }
}
