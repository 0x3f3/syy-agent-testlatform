package com.syy.testplatform.ai.test.entity;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data @Entity @Table(name="knowledge_chunk",indexes={@Index(name="idx_knowledge_source",columnList="source")})
public class KnowledgeChunk {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(length=512) private String source;
 @Lob @Column(columnDefinition="LONGTEXT") private String content;
 @Lob @Column(columnDefinition="LONGTEXT") private String embeddingJson;
 private LocalDateTime createdAt=LocalDateTime.now();
}
