package com.syy.testplatform.ai.test.schedule;

public record CreateTestScheduleRequest(Long documentId, String scheduleName, String taskName,
                                        String baseUrl, String cronExpression, Boolean enabled) { }
