package com.demo.repair.util;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.JWTValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expire}")
    private Long expire;

    /**
     * 生成 token。额外写入自有的 loginTime（毫秒）字段，
     * 供 TokenRevoker 与吊销水位线比较（不依赖标准 iat 的秒级精度）
     */
    public String createToken(Long userId, String username, String roleCode) {
        Date now = new Date();
        Date expireAt = new Date(now.getTime() + expire * 1000);
        return JWT.create()
                .setPayload("userId", userId)
                .setPayload("username", username)
                .setPayload("roleCode", roleCode)
                .setPayload("loginTime", now.getTime())
                .setIssuedAt(now)
                .setExpiresAt(expireAt)
                .setKey(secret.getBytes(StandardCharsets.UTF_8))
                .sign();
    }

    /**
     * 解析并校验 token，失败返回 null
     */
    public JWT parseToken(String token) {
        try {
            JWT jwt = JWTUtil.parseToken(token);
            jwt.setKey(secret.getBytes(StandardCharsets.UTF_8));
            if (!jwt.verify()) {
                return null;
            }
            // 校验是否过期
            JWTValidator.of(jwt).validateDate();
            return jwt;
        } catch (Exception e) {
            log.debug("token 解析/校验失败: {}", e.getMessage());
            return null;
        }
    }
}
