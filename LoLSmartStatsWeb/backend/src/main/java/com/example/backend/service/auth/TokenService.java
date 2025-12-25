package com.example.backend.service.auth;

import com.example.backend.entity.RefreshToken;
import com.example.backend.exception.BizException;
import com.example.backend.repository.RefreshTokenRepository;
import com.example.backend.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class TokenService {
    private final JwtUtil jwtUtil;
    private final RefreshTokenRepository refreshTokenRepository;

    private final long accessTtlSeconds;
    private final long refreshTtlSeconds;

    public TokenService(JwtUtil jwtUtil,
                        RefreshTokenRepository refreshTokenRepository,
                        @Value("${app.jwt.access-ttl-seconds:1800}") long accessTtlSeconds,
                        @Value("${app.jwt.refresh-ttl-seconds:2592000}") long refreshTtlSeconds) {
        this.jwtUtil = jwtUtil;
        this.refreshTokenRepository = refreshTokenRepository;
        this.accessTtlSeconds = accessTtlSeconds;
        this.refreshTtlSeconds = refreshTtlSeconds;
    }

    public String issueAccessToken(String userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("typ", "access");
        return jwtUtil.generate(userId, accessTtlSeconds, claims);
    }

    public String issueAndStoreRefreshToken(String userId) {
        // refresh token 不必是 JWT，这里用随机串
        String token = KeyGenerators.string().generateKey();
        RefreshToken rt = new RefreshToken();
        rt.setToken(token);
        rt.setUserId(userId);
        rt.setCreatedAt(LocalDateTime.now());
        rt.setExpiresAt(LocalDateTime.now().plusSeconds(refreshTtlSeconds));
        rt.setRevoked(false);
        refreshTokenRepository.save(rt);
        return token;
    }

    public void revoke(String refreshToken) {
        RefreshToken rt = refreshTokenRepository.findById(refreshToken).orElse(null);
        if (rt != null) {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
        }
    }

    /**
     * 校验 refresh token，并进行“旋转刷新”：吊销旧 token，签发并保存新 token。
     */
    public RefreshResult rotateRefreshToken(String refreshToken) {
        RefreshToken rt = refreshTokenRepository.findById(refreshToken)
                .orElseThrow(() -> new BizException("UNAUTHORIZED", "refresh token 无效"));
        if (rt.isRevoked()) {
            throw new BizException("UNAUTHORIZED", "refresh token 已失效");
        }
        if (rt.getExpiresAt() != null && rt.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BizException("UNAUTHORIZED", "refresh token 已过期");
        }

        // rotate
        rt.setRevoked(true);
        refreshTokenRepository.save(rt);

        String newRefresh = issueAndStoreRefreshToken(rt.getUserId());
        String newAccess = issueAccessToken(rt.getUserId());
        return new RefreshResult(newAccess, newRefresh);
    }

    public static class RefreshResult {
        private final String accessToken;
        private final String refreshToken;

        public RefreshResult(String accessToken, String refreshToken) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }

        public String accessToken() { return accessToken; }
        public String refreshToken() { return refreshToken; }
    }
}

