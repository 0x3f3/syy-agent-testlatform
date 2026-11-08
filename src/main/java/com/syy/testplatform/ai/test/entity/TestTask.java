package com.syy.testplatform.ai.test.entity;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import java.time.LocalDateTime;
@Data @Entity @Table(name="test_task",indexes={@Index(name="idx_task_created_at",columnList="created_at")})
public class TestTask {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(length=255) private String taskName;
 @Column(name="document_id") private Long documentId;
 @Column(length=1024) private String baseUrl;
 @Column(length=24) private String status;
 private Integer totalCount=0;
 private Integer passedCount=0;
 private Integer failedCount=0;
 private Integer errorCount=0;
 @Column(name="created_at") private LocalDateTime createdAt=LocalDateTime.now();
 @Column(name="started_at") private LocalDateTime startedAt;
 @Column(name="finished_at") private LocalDateTime finishedAt;
 @Column(length=2000) private String errorMessage;
 @JsonIgnore @Lob @Column(name="request_json", columnDefinition="LONGTEXT") private String requestJson;
}
