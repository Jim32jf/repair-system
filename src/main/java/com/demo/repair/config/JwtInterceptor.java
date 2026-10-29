package com.demo.repair.config;

import cn.hutool.core.util.StrUtil;
import cn.hutool.jwt.JWT;
import com.demo.repair.common.LoginUser;
import com.demo.repair.common.RequireRole;
import com.demo.repair.common.UserContext;
import com.demo.repair.util.JwtUtil;
import com.demo.repair.util.TokenRevoker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;

/**
 * JWT 登录拦截器
 */
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    private final TokenRevoker tokenRevoker;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("Authorization");
        if (StrUtil.isBlank(token)) {
            token = request.getHeader("token");
        }
        if (StrUtil.isNotBlank(token) && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        if (StrUtil.isBlank(token)) {
            writeUnauthorized(response);
            return false;
        }

        JWT jwt = jwtUtil.parseToken(token);
        if (jwt == null) {
            writeUnauthorized(response);
            return false;
        }

        Long userId = jwt.getPayloads().getLong("userId");
        String username = jwt.getPayloads().getStr("username");
        String roleCode = jwt.getPayloads().getStr("roleCode");

        // 吊销校验：登出/禁用/删除/重置密码后，此前签发的 token 一律作废
        Long loginTime = jwt.getPayloads().getLong("loginTime");
        if (tokenRevoker.isRevoked(userId, loginTime)) {
            writeRevoked(response);
            return false;
        }

        UserContext.set(new LoginUser(userId, username, roleCode));

        // 角色权限校验：方法上的注解优先于类上的注解
        if (handler instanceof HandlerMethod) {
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
            if (requireRole == null) {
                requireRole = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
            }
            if (requireRole != null && !Arrays.asList(requireRole.value()).contains(roleCode)) {
                UserContext.clear();
                writeForbidden(response);
                return false;
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"未登录或登录已过期\",\"data\":null}");
    }

    private void writeRevoked(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"登录已失效，请重新登录\",\"data\":null}");
    }

    private void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":403,\"message\":\"无权限访问\",\"data\":null}");
    }
}
