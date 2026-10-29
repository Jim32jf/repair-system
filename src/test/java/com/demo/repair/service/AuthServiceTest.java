package com.demo.repair.service;

import com.demo.repair.common.BusinessException;
import com.demo.repair.common.LoginUser;
import com.demo.repair.common.UserContext;
import com.demo.repair.dto.LoginDTO;
import com.demo.repair.entity.SysRole;
import com.demo.repair.entity.SysUser;
import com.demo.repair.mapper.SysRoleMapper;
import com.demo.repair.mapper.SysUserMapper;
import com.demo.repair.service.impl.AuthServiceImpl;
import com.demo.repair.util.JwtUtil;
import com.demo.repair.util.TokenRevoker;
import com.demo.repair.vo.LoginVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录：BCrypt 校验、历史 MD5 兼容、禁用账号拦截
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    /** 明文 123456 的 BCrypt 哈希 */
    private static final String BCRYPT_123456 =
            "$2a$10$FBCAh33XjE2PgnFIIn6jDeAau6YccfIyq751nZaTCcHpldEqsw796";

    /** 明文 123456 的 MD5（历史数据） */
    private static final String MD5_123456 = "e10adc3949ba59abbe56e057f20f883e";

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private SysRoleMapper roleMapper;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private TokenRevoker tokenRevoker;

    @InjectMocks
    private AuthServiceImpl authService;

    private SysUser buildUser(String password, int status) {
        SysUser user = new SysUser();
        user.setId(2L);
        user.setUsername("student");
        user.setRealName("张三");
        user.setPassword(password);
        user.setRoleId(1L);
        user.setStatus(status);
        return user;
    }

    private LoginDTO loginDTO(String password) {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("student");
        dto.setPassword(password);
        return dto;
    }

    private void stubRoleAndToken() {
        SysRole role = new SysRole();
        role.setRoleCode("STUDENT");
        role.setRoleName("学生");
        when(roleMapper.selectById(1L)).thenReturn(role);
        when(jwtUtil.createToken(anyLong(), anyString(), anyString())).thenReturn("mock-token");
    }

    @Test
    @DisplayName("BCrypt 密码登录成功并返回 token")
    void loginWithBcrypt() {
        when(userMapper.selectOne(any())).thenReturn(buildUser(BCRYPT_123456, 1));
        stubRoleAndToken();

        LoginVO vo = authService.login(loginDTO("123456"));

        assertEquals("mock-token", vo.getToken());
        assertEquals("STUDENT", vo.getRoleCode());
    }

    @Test
    @DisplayName("历史 MD5 密码仍可登录（平滑迁移）")
    void loginWithLegacyMd5() {
        when(userMapper.selectOne(any())).thenReturn(buildUser(MD5_123456, 1));
        stubRoleAndToken();

        LoginVO vo = authService.login(loginDTO("123456"));

        assertEquals("mock-token", vo.getToken());
    }

    @Test
    @DisplayName("密码错误：统一提示且不泄露账号是否存在")
    void wrongPassword() {
        when(userMapper.selectOne(any())).thenReturn(buildUser(BCRYPT_123456, 1));

        BusinessException e = assertThrows(BusinessException.class,
                () -> authService.login(loginDTO("bad-password")));
        assertEquals(400, e.getCode());
        assertEquals("用户名或密码错误", e.getMessage());
    }

    @Test
    @DisplayName("账号被禁用：403")
    void disabledAccount() {
        when(userMapper.selectOne(any())).thenReturn(buildUser(BCRYPT_123456, 0));

        BusinessException e = assertThrows(BusinessException.class,
                () -> authService.login(loginDTO("123456")));
        assertEquals(403, e.getCode());
    }

    @Test
    @DisplayName("用户不存在：与密码错误同一提示")
    void userNotFound() {
        when(userMapper.selectOne(any())).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> authService.login(loginDTO("123456")));
        assertEquals(400, e.getCode());
        assertEquals("用户名或密码错误", e.getMessage());
    }

    @Test
    @DisplayName("登出吊销当前用户 token")
    void logoutRevokesCurrentUserToken() {
        UserContext.set(new LoginUser(9L, "admin", "ADMIN"));
        try {
            authService.logout();
            verify(tokenRevoker).revoke(9L);
        } finally {
            UserContext.clear();
        }
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }
}
