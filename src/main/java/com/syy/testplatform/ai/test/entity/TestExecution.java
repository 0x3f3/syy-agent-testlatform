package com.syy.testplatform.ai.test.entity;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data @Entity @Table(name="test_execution",indexes={@Index(name="idx_execution_task_id",columnList="task_id"),@Index(name="idx_execution_case_id",columnList="case_id"),@Index(name="idx_execution_time",columnList="execute_time")})
public class TestExecution {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="task_id") private Long taskId; @Column(name="case_id") private Long caseId; @Column(name="api_id") private Long apiId;
 @Column(length=1024) private String requestUrl;
 @Column(length=12) private String requestMethod;
 @Lob @Column(columnDefinition="LONGTEXT") private String requestHeadersJson;
 @Lob @Column(columnDefinition="LONGTEXT") private String requestBody;
 private Integer responseStatus;
 @Lob @Column(columnDefinition="LONGTEXT") private String responseHeadersJson;
 @Lob @Column(columnDefinition="LONGTEXT") private String actualResult;
 private Long durationMs;
 @Column(length=16) private String status;
 private Integer score;
 private Boolean judgeAvailable;
 @Column(length=2000) private String evaluationReason;
 @Column(length=2000) private String suggestion;
 @Column(name="execute_time") private LocalDateTime executeTime=LocalDateTime.now();
}
