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

    /**
     * 头像。两种取值（判据是前缀，见 UserService）：
     * <ul>
     *   <li>{@code "default:N"}（N=1..9）—— 前端 public/avatars/N.svg。注册时随机分配一个，
     *       用户不主动改就一直不变。</li>
     *   <li>{@code "avatar/{userId}/{uuid}.png"} —— 用户自己传的，存在 OSS 里的对象 key。</li>
     * </ul>
     *
     * <p>本服务是 {@code ddl-auto: validate}，这一列**不会**被 Hibernate 自动建出来，
     * DDL 在 {@code db/migration.sql} 末尾。
     */
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

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
