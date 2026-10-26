package com.demo.repair.service.impl;

import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demo.repair.common.BusinessException;
import com.demo.repair.dto.LoginDTO;
import com.demo.repair.entity.SysRole;
import com.demo.repair.entity.SysUser;
import com.demo.repair.mapper.SysRoleMapper;
import com.demo.repair.mapper.SysUserMapper;
import com.demo.repair.service.AuthService;
import com.demo.repair.util.JwtUtil;
import com.demo.repair.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper userMapper;

    private final SysRoleMapper roleMapper;

    private final JwtUtil jwtUtil;

    @Override
    public LoginVO login(LoginDTO dto) {
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, dto.getUsername())
        );
        if (user == null) {
            throw new BusinessException(400, "用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(403, "账号已被禁用");
        }
        // 教学项目使用 MD5，生产环境应使用 BCrypt 加盐
        String encoded = SecureUtil.md5(dto.getPassword());
        if (!encoded.equals(user.getPassword())) {
            throw new BusinessException(400, "用户名或密码错误");
        }

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
}
