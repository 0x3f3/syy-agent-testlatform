package com.syy.aicodehelper.ai.test.schedule;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class CronScheduleTest {
    @Test
    void calculatesNextDailyExecutionUsingSpringSixFieldCron() {
        CronExpression cron = CronExpression.parse("0 0 9 * * *");
        LocalDateTime next = cron.next(LocalDateTime.of(2026, 11, 8, 8, 30));
        assertEquals(LocalDateTime.of(2026, 11, 8, 9, 0), next);
    }

    @Test
    void rejectsInvalidCronExpressions() {
        assertThrows(IllegalArgumentException.class, () -> CronExpression.parse("0 9 * * *"));
    }
}
