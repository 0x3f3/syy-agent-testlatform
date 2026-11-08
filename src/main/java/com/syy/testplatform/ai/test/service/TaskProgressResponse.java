package com.syy.testplatform.ai.test.service;

import java.time.LocalDateTime;

public record TaskProgressResponse(
        Long taskId,
        String status,
        int totalCount,
        int completedCount,
        int passedCount,
        int failedCount,
        int errorCount,
        int progressPercent,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime updatedAt,
        String errorMessage,
        boolean reportAvailable) { }
