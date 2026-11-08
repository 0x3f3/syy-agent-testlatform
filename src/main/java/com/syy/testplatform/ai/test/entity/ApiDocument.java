package com.syy.testplatform.ai.test.entity;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import java.time.LocalDateTime;
@Data @Entity @Table(name="api_document")
public class ApiDocument {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,length=255) private String name;
 @Column(length=255) private String title;
 @Column(length=64) private String version;
 @Column(length=1024) private String serverUrl;
 @Column(length=32) private String specificationVersion;
 @JsonIgnore @Lob @Column(columnDefinition="LONGTEXT") private String rawDocument;
 @Column(name="raw_document_format",length=16,nullable=false) private String rawDocumentFormat="JSON";
 private LocalDateTime importedAt=LocalDateTime.now();
}
