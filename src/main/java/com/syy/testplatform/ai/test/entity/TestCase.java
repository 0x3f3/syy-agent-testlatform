package com.syy.testplatform.ai.test.entity;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import java.time.LocalDateTime;
@Data @Entity @Table(name="test_case",indexes={@Index(name="idx_case_api_id",columnList="api_id")}, uniqueConstraints={@UniqueConstraint(name="uk_test_case_api_fingerprint",columnNames={"api_id","fingerprint"})})
public class TestCase {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="api_id",nullable=false) private Long apiId;
 @Column(nullable=false,length=255) private String caseName;
 @Column(length=40) private String caseType;
 @Column(length=12) private String priority;
 @Column(length=1000) private String description;
 @JsonIgnore @Lob @Column(columnDefinition="LONGTEXT") private String requestJson;
 @Lob @Column(columnDefinition="LONGTEXT") private String expectResult;
 @Lob @Column(columnDefinition="LONGTEXT") private String assertionsJson;
 @Column(length=64) private String fingerprint;
 private Boolean enabled=true;
 private LocalDateTime createdAt=LocalDateTime.now();
}
