package com.kh.back.service.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisTokenBlacklistService {

    private static final String BLACKLIST_KEY_PREFIX = "auth:blacklist:";

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Access Token을 블랙리스트에 등록한다.
     * TTL은 토큰 만료 시각까지로 설정해 Redis 메모리를 자동 정리한다.
     */
    public void addToBlacklist(String accessToken, long expiresAtMillis) {
        long ttlMillis = expiresAtMillis - System.currentTimeMillis();
        if (ttlMillis <= 0) {
            log.info("[blacklist] token already expired, skip blacklisting");
            return;
        }
        stringRedisTemplate.opsForValue().set(
                buildKey(accessToken), "1", Duration.ofMillis(ttlMillis)
        );
        log.info("[blacklist] access token added to blacklist, ttlMillis={}", ttlMillis);
    }

    /**
     * 해당 Access Token이 블랙리스트에 있는지 확인한다.
     */
    public boolean isBlacklisted(String accessToken) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(buildKey(accessToken)));
    }

    // 원본 토큰 대신 SHA-256 해시값을 키로 사용해 Redis 메모리 절약
    private String buildKey(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return BLACKLIST_KEY_PREFIX + HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable.", e);
        }
    }
}
