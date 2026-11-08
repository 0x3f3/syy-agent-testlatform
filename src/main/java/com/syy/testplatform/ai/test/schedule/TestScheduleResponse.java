package com.syy.testplatform.ai.test.schedule;

import java.time.LocalDateTime;

public record TestScheduleResponse(Long id, String scheduleName, Long documentId, String taskName,
                                   String baseUrl, String cronExpression, Boolean enabled,
                                   LocalDateTime nextRunAt, LocalDateTime lastRunAt, Long lastTaskId) { }
