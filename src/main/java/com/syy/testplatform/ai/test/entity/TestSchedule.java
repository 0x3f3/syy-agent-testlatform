package com.syy.testplatform.ai.test.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "test_schedule", indexes = {
        @Index(name = "idx_schedule_due", columnList = "enabled, next_run_at"),
        @Index(name = "idx_schedule_document", columnList = "document_id")
})
public class TestSchedule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "schedule_name", nullable = false, length = 255)
    private String scheduleName;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "task_name", length = 255)
    private String taskName;

    @Column(name = "base_url", length = 1024)
    private String baseUrl;

    @Column(name = "cron_expression", nullable = false, length = 100)
    private String cronExpression;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "next_run_at", nullable = false)
    private LocalDateTime nextRunAt;

    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;

    @Column(name = "last_task_id")
    private Long lastTaskId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
