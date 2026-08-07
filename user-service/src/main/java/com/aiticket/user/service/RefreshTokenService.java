package com.aiticket.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Refresh Token 管理服务
 * 使用Redis存储Refresh Token，支持主动撤销
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final StringRedisTemplate redisTemplate;

    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final long REFRESH_TOKEN_EXPIRE_DAYS = 7;

    /**
     * 保存Refresh Token到Redis
     *
     * @param userId       用户ID
     * @param refreshToken Refresh Token
     */
    public void saveRefreshToken(Long userId, String refreshToken) {
        String key = REFRESH_TOKEN_PREFIX + userId;
        redisTemplate.opsForValue().set(key, refreshToken, REFRESH_TOKEN_EXPIRE_DAYS, TimeUnit.DAYS);
        log.debug("Saved refresh token for userId: {}", userId);
    }

    /**
     * 从Redis获取Refresh Token
     *
     * @param userId 用户ID
     * @return Refresh Token，不存在返回null
     */
    public String getRefreshToken(Long userId) {
        String key = REFRESH_TOKEN_PREFIX + userId;
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 验证Refresh Token是否匹配
     *
     * @param userId       用户ID
     * @param refreshToken 输入的Refresh Token
     * @return 是否匹配
     */
    public boolean validateRefreshToken(Long userId, String refreshToken) {
        String storedToken = getRefreshToken(userId);
        if (storedToken == null) {
            log.warn("Refresh token not found for userId: {}", userId);
            return false;
        }
        boolean matches = storedToken.equals(refreshToken);
        if (!matches) {
            log.warn("Refresh token mismatch for userId: {}", userId);
        }
        return matches;
    }

    /**
     * 删除Refresh Token（用户登出）
     *
     * @param userId 用户ID
     */
    public void deleteRefreshToken(Long userId) {
        String key = REFRESH_TOKEN_PREFIX + userId;
        redisTemplate.delete(key);
        log.debug("Deleted refresh token for userId: {}", userId);
    }

    /**
     * 检查Refresh Token是否存在
     *
     * @param userId 用户ID
     * @return 是否存在
     */
    public boolean hasRefreshToken(Long userId) {
        String key = REFRESH_TOKEN_PREFIX + userId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
