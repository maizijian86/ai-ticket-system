package com.aiticket.user.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Entity
@Table(name = "user_skill")
public class UserSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", unique = true, nullable = false)
    private Long userId;

    @Column(name = "user_name")
    private String userName;

    // MySQL JSON for skill tags array: ["java", "mysql", "redis"]
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skill_tags", columnDefinition = "json")
    private String[] skillTags;

    // MySQL JSON for expertise level: {"java": 5, "mysql": 4}
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "expertise_level", columnDefinition = "json")
    private Map<String, Integer> expertiseLevel;

    // Metrics
    @Column(name = "total_resolved")
    private Integer totalResolved = 0;

    @Column(name = "avg_resolution_hours", precision = 6, scale = 2)
    private BigDecimal avgResolutionHours;

    @Column(name = "satisfaction_score", precision = 3, scale = 2)
    private BigDecimal satisfactionScore = new BigDecimal("5.00");

    // Load management
    @Column(name = "current_load")
    private Integer currentLoad = 0;

    @Column(name = "max_load")
    private Integer maxLoad = 10;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;
}
