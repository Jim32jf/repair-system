package com.demo.repair.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Token 吊销水位线：比较逻辑与 Redis 异常降级（fail-open）
 */
@ExtendWith(MockitoExtension.class)
class TokenRevokerTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private TokenRevoker tokenRevoker;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(tokenRevoker, "expireSeconds", 86400L);
    }

    @Test
    @DisplayName("token 签发时间早于水位线 → 判定已吊销")
    void tokenIssuedBeforeEpochIsRevoked() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("repair:token:epoch:7")).thenReturn("2000");

        assertTrue(tokenRevoker.isRevoked(7L, 1000L));
    }

    @Test
    @DisplayName("token 签发时间晚于水位线（吊销后重新登录）→ 放行")
    void tokenIssuedAfterEpochPasses() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("repair:token:epoch:7")).thenReturn("2000");

        assertFalse(tokenRevoker.isRevoked(7L, 3000L));
    }

    @Test
    @DisplayName("无水位线记录 → 放行")
    void noEpochPasses() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);

        assertFalse(tokenRevoker.isRevoked(7L, 1000L));
    }

    @Test
    @DisplayName("Redis 异常 → fail-open 放行，不阻断已登录用户")
    void redisFailureFailsOpen() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("redis down"));

        assertFalse(tokenRevoker.isRevoked(7L, 1000L));
    }

    @Test
    @DisplayName("旧版 token 无 loginTime 字段 → 放行（升级兼容）")
    void legacyTokenWithoutLoginTimePasses() {
        assertFalse(tokenRevoker.isRevoked(7L, null));
    }
}
