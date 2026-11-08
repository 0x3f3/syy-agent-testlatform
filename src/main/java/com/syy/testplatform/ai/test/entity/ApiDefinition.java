package com.syy.testplatform.ai.test.entity;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
@Data @Entity @Table(name="api_definition",indexes={@Index(name="idx_api_document_id",columnList="document_id"),@Index(name="idx_api_method_path",columnList="method,path")})
public class ApiDefinition {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="document_id") private Long documentId;
 @Column(length=512,nullable=false) private String path;
 @Column(length=12,nullable=false) private String method;
 @Column(length=255) private String operationId;
 @Column(length=512) private String description;
 @Column(length=1024) private String serverUrl;
 @Column(length=512) private String tags;
 @Lob @Column(columnDefinition="LONGTEXT") private String parametersJson;
 @Lob @Column(columnDefinition="LONGTEXT") private String requestBodySchemaJson;
 @JsonIgnore @Lob @Column(columnDefinition="LONGTEXT") private String requestExampleJson;
 @Lob @Column(columnDefinition="LONGTEXT") private String responsesJson;
 @Lob @Column(columnDefinition="LONGTEXT") private String securityJson;
 @JsonIgnore @Lob @Column(columnDefinition="LONGTEXT") private String rawOperationJson;
}
