package com.demo.repair.service;

import cn.hutool.crypto.digest.BCrypt;
import com.demo.repair.common.BusinessException;
import com.demo.repair.dto.UserCreateDTO;
import com.demo.repair.entity.SysUser;
import com.demo.repair.mapper.SysRoleMapper;
import com.demo.repair.mapper.SysUserMapper;
import com.demo.repair.service.impl.UserServiceImpl;
import com.demo.repair.util.TokenRevoker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户模块：BCrypt 存储、重名拦截、重置密码
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private SysRoleMapper roleMapper;

    @Mock
    private TokenRevoker tokenRevoker;

    @InjectMocks
    private UserServiceImpl userService;

    private UserCreateDTO createDTO() {
        UserCreateDTO dto = new UserCreateDTO();
        dto.setUsername("newbie");
        dto.setPassword("MyPwd123");
        dto.setRealName("新用户");
        dto.setRoleId(1L);
        return dto;
    }

    @Test
    @DisplayName("新建用户以 BCrypt 加盐哈希存密码")
    void createStoresBcryptHash() {
        when(userMapper.selectCount(any())).thenReturn(0L);

        userService.create(createDTO());

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).insert(captor.capture());
        String stored = captor.getValue().getPassword();

        assertTrue(stored.startsWith("$2a$"), "应为 BCrypt 格式");
        assertTrue(BCrypt.checkpw("MyPwd123", stored));
        assertEquals(1, captor.getValue().getStatus(), "默认启用");
    }

    @Test
    @DisplayName("用户名重复：400 拒绝且不落库")
    void duplicateUsernameRejected() {
        when(userMapper.selectCount(any())).thenReturn(1L);

        BusinessException e = assertThrows(BusinessException.class, () -> userService.create(createDTO()));
        assertEquals(400, e.getCode());
        verify(userMapper, never()).insert(any());
    }

    @Test
    @DisplayName("重置密码：写入 123456 的 BCrypt 哈希")
    void resetPasswordUsesBcrypt() {
        SysUser existing = new SysUser();
        existing.setId(5L);
        when(userMapper.selectById(5L)).thenReturn(existing);

        userService.resetPassword(5L);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        assertTrue(BCrypt.checkpw("123456", captor.getValue().getPassword()));
        verify(tokenRevoker).revoke(5L);
    }

    @Test
    @DisplayName("禁用用户：写库后立即吊销其 token")
    void disableRevokesToken() {
        SysUser existing = new SysUser();
        existing.setId(8L);
        existing.setStatus(1);
        when(userMapper.selectById(8L)).thenReturn(existing);

        userService.updateStatus(8L, 0);

        verify(userMapper).updateById(any());
        verify(tokenRevoker).revoke(8L);
    }
}
