package com.demo.repair.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Token 吊销：按用户记录"失效水位线"（毫秒时间戳），
 * 早于水位线签发的 token 一律作废。
 * 触发场景：登出、禁用用户、删除用户、重置密码。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenRevoker {

    private static final String EPOCH_KEY_PREFIX = "repair:token:epoch:";

    private final StringRedisTemplate redisTemplate;

    /** 水位线保留时长：超过 token 最长有效期后，旧 token 已自然过期，无需再记 */
    @Value("${jwt.expire:86400}")
    private long expireSeconds;

    /** 吊销该用户当前已签发的全部 token（TTL 内的新 token 不受影响） */
    public void revoke(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    EPOCH_KEY_PREFIX + userId,
                    String.valueOf(System.currentTimeMillis()),
                    expireSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            // 写入失败意味着本次吊销未生效，与项目降级风格一致：记 warn 不阻断主流程
            log.warn("写入 token 吊销水位线失败, userId={}: {}", userId, e.getMessage());
        }
    }

    /** token 是否已被吊销；Redis 异常时放行（fail-open），与登录限流策略一致 */
    public boolean isRevoked(Long userId, Long loginTimeMillis) {
        // 升级前签发的旧 token 没有 loginTime 字段，视为未吊销（最长 24h 后自然过期）
        if (userId == null || loginTimeMillis == null) {
            return false;
        }
        try {
            String epoch = redisTemplate.opsForValue().get(EPOCH_KEY_PREFIX + userId);
            return epoch != null && loginTimeMillis < Long.parseLong(epoch);
        } catch (Exception e) {
            log.warn("读取 token 吊销水位线失败，放行, userId={}: {}", userId, e.getMessage());
            return false;
        }
    }
}
