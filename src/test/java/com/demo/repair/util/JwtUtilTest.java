package com.demo.repair.util;

import cn.hutool.jwt.JWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * JWT 生成 / 校验：签名、过期、密钥不匹配
 */
class JwtUtilTest {

    private static final String SECRET = "unit-test-secret-value-0123456789";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expire", 3600L);
    }

    @Test
    @DisplayName("生成的 token 可解析出完整载荷")
    void createAndParse() {
        String token = jwtUtil.createToken(7L, "student", "STUDENT");

        JWT jwt = jwtUtil.parseToken(token);

        assertNotNull(jwt);
        assertEquals(7L, jwt.getPayloads().getLong("userId"));
        assertEquals("student", jwt.getPayloads().getStr("username"));
        assertEquals("STUDENT", jwt.getPayloads().getStr("roleCode"));
    }

    @Test
    @DisplayName("乱改内容的 token 解析为 null")
    void garbageToken() {
        assertNull(jwtUtil.parseToken("not-a-jwt-token"));
    }

    @Test
    @DisplayName("换密钥后旧 token 签名校验失败")
    void otherSecretRejected() {
        JwtUtil attacker = new JwtUtil();
        ReflectionTestUtils.setField(attacker, "secret", "another-secret-value-9999999999");
        ReflectionTestUtils.setField(attacker, "expire", 3600L);

        String forged = attacker.createToken(1L, "hacker", "ADMIN");

        assertNull(jwtUtil.parseToken(forged));
    }

    @Test
    @DisplayName("已过期的 token 解析为 null")
    void expiredTokenRejected() {
        JwtUtil shortLived = new JwtUtil();
        ReflectionTestUtils.setField(shortLived, "secret", SECRET);
        ReflectionTestUtils.setField(shortLived, "expire", -10L);

        String expired = shortLived.createToken(1L, "user", "STUDENT");

        assertNull(jwtUtil.parseToken(expired));
    }
}
