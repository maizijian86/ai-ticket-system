package com.aiticket.user.entity;

import com.aiticket.common.entity.BaseEntity;
import com.aiticket.common.enums.Role;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(unique = true, nullable = false, length = 100)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(length = 100)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Role role = Role.USER;

    @Column(length = 20)
    private String status = "active";

    @Column(name = "last_login_at")
    private java.time.LocalDateTime lastLoginAt;

    @Column(name = "github_username", length = 100)
    private String githubUsername;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "github_repos", columnDefinition = "json")
    private List<Map<String, String>> githubRepos;
}
