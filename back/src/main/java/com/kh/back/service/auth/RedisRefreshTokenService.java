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
public class RedisRefreshTokenService {

    private static final String REFRESH_KEY_PREFIX = "auth:refresh:";

    private final StringRedisTemplate stringRedisTemplate;

    public void saveRefreshToken(Long memberId, String refreshToken, long expiresAtMillis) {
        long ttlMillis = Math.max(expiresAtMillis - System.currentTimeMillis(), 1L);
        stringRedisTemplate.opsForValue().set(buildRefreshKey(memberId), hash(refreshToken), Duration.ofMillis(ttlMillis));
        log.info("[saveRefreshToken] memberId={}, ttlMillis={}", memberId, ttlMillis);
    }

    public boolean matches(Long memberId, String refreshToken) {
        String storedHash = stringRedisTemplate.opsForValue().get(buildRefreshKey(memberId));
        return storedHash != null && storedHash.equals(hash(refreshToken));
    }

    public boolean exists(Long memberId) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(buildRefreshKey(memberId)));
    }

    public void deleteRefreshToken(Long memberId) {
        stringRedisTemplate.delete(buildRefreshKey(memberId));
        log.info("[deleteRefreshToken] memberId={}", memberId);
    }

    public void deleteAllTokensByMember(Long memberId) {
        deleteRefreshToken(memberId);
        log.warn("[deleteAllTokensByMember] memberId={}", memberId);
    }

    private String buildRefreshKey(Long memberId) {
        return REFRESH_KEY_PREFIX + memberId;
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable.", e);
        }
    }
}
