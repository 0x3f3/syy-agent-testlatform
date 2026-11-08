package com.syy.testplatform.ai.test.entity;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data @Entity @Table(name="test_report",indexes={@Index(name="idx_report_task_id",columnList="task_id")})
public class TestReport {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="task_id") private Long taskId; private Integer totalCount; private Integer successCount; private Integer failCount; private Integer errorCount;
 private Double passRate; private Double averageScore;
 @Lob @Column(columnDefinition="LONGTEXT") private String aiAnalysis;
 private LocalDateTime createTime=LocalDateTime.now();
}
