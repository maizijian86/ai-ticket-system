package com.aiticket.ai.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "knowledge_base")
public class KnowledgeBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // Note: MySQL doesn't support vector type, using FULLTEXT search instead
    // Embedding stored as JSON for future use when vector support is needed

    @Column(length = 50)
    private String category;

    @Column(name = "source_type", length = 50)
    private String sourceType;  // 'ticket', 'manual', 'document'

    @Column(name = "source_id")
    private Long sourceId;

    // Quality metrics
    @Column(name = "view_count")
    private Integer viewCount = 0;

    @Column(name = "helpful_count")
    private Integer helpfulCount = 0;

    @Column(name = "not_helpful_count")
    private Integer notHelpfulCount = 0;

    // Status: draft, published, archived
    @Column(length = 20)
    private String status = "draft";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
