package com.demo.repair.service.impl;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.extra.servlet.ServletUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demo.repair.common.BusinessException;
import com.demo.repair.common.UserContext;
import com.demo.repair.dto.LoginDTO;
import com.demo.repair.entity.SysRole;
import com.demo.repair.entity.SysUser;
import com.demo.repair.mapper.SysRoleMapper;
import com.demo.repair.mapper.SysUserMapper;
import com.demo.repair.service.AuthService;
import com.demo.repair.util.JwtUtil;
import com.demo.repair.util.TokenRevoker;
import com.demo.repair.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String FAIL_KEY_PREFIX = "repair:login:fail:";

    private final SysUserMapper userMapper;

    private final SysRoleMapper roleMapper;

    private final JwtUtil jwtUtil;

    private final StringRedisTemplate redisTemplate;

    private final TokenRevoker tokenRevoker;

    /** 同一 IP 连续登录失败达到该次数后锁定 */
    @Value("${repair.login.max-fail-count:10}")
    private int maxFailCount;

    /** 锁定时长（分钟） */
    @Value("${repair.login.lock-minutes:5}")
    private long lockMinutes;

    @Override
    public LoginVO login(LoginDTO dto) {
        // 登录限流：按客户端 IP 计失败次数，防公网暴力破解；Redis 异常时放行，不阻断正常登录
        String failKey = FAIL_KEY_PREFIX + currentClientIp();
        checkLoginLock(failKey);

        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, dto.getUsername())
        );
        if (user == null) {
            recordLoginFail(failKey);
            throw new BusinessException(400, "用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            // 禁用账号不计失败：其密码可能本来就对，且账号已不可用，爆破无收益
            throw new BusinessException(403, "账号已被禁用");
        }
        // BCrypt 加盐哈希校验；兼容历史 MD5 数据（$2 开头才是 BCrypt 格式），迁移完成后可删掉 MD5 分支
        String stored = user.getPassword();
        boolean matched = stored != null && (stored.startsWith("$2")
                ? BCrypt.checkpw(dto.getPassword(), stored)
                : SecureUtil.md5(dto.getPassword()).equals(stored));
        if (!matched) {
            recordLoginFail(failKey);
            throw new BusinessException(400, "用户名或密码错误");
        }
        clearLoginFail(failKey);

        SysRole role = roleMapper.selectById(user.getRoleId());
        String roleCode = role == null ? "" : role.getRoleCode();

        String token = jwtUtil.createToken(user.getId(), user.getUsername(), roleCode);

        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setRoleCode(roleCode);
        vo.setRoleName(role == null ? "" : role.getRoleName());
        return vo;
    }

    @Override
    public void logout() {
        // 吊销当前用户此前签发的全部 token；token 里的身份来自拦截器写入的 UserContext
        tokenRevoker.revoke(UserContext.getUserId());
    }

    /** 取真实客户端 IP：Nginx 反代场景优先读 X-Forwarded-For 等头 */
    private String currentClientIp() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes)) {
            return "unknown";
        }
        HttpServletRequest request = ((ServletRequestAttributes) attrs).getRequest();
        return ServletUtil.getClientIP(request);
    }

    private void checkLoginLock(String failKey) {
        try {
            String count = redisTemplate.opsForValue().get(failKey);
            if (count != null && Integer.parseInt(count) >= maxFailCount) {
                throw new BusinessException(429, "登录失败次数过多，已锁定 " + lockMinutes + " 分钟，请稍后再试");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("读取登录限流计数失败，放行: {}", e.getMessage());
        }
    }

    private void recordLoginFail(String failKey) {
        try {
            Long count = redisTemplate.opsForValue().increment(failKey);
            if (count != null && count == 1L) {
                redisTemplate.expire(failKey, lockMinutes, TimeUnit.MINUTES);
            }
        } catch (Exception e) {
            log.warn("记录登录失败计数异常: {}", e.getMessage());
        }
    }

    private void clearLoginFail(String failKey) {
        try {
            redisTemplate.delete(failKey);
        } catch (Exception e) {
            log.warn("清除登录失败计数异常: {}", e.getMessage());
        }
    }
}
