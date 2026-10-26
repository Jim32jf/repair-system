package com.demo.repair.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demo.repair.common.BusinessException;
import com.demo.repair.common.PageResult;
import com.demo.repair.common.UserContext;
import com.demo.repair.dto.UserCreateDTO;
import com.demo.repair.dto.UserQueryDTO;
import com.demo.repair.dto.UserUpdateDTO;
import com.demo.repair.entity.SysRole;
import com.demo.repair.entity.SysUser;
import com.demo.repair.mapper.SysRoleMapper;
import com.demo.repair.mapper.SysUserMapper;
import com.demo.repair.service.UserService;
import com.demo.repair.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final SysUserMapper userMapper;

    private final SysRoleMapper roleMapper;

    @Override
    public PageResult<UserVO> page(UserQueryDTO query) {
        Page<SysUser> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .like(StrUtil.isNotBlank(query.getUsername()), SysUser::getUsername, query.getUsername())
                .like(StrUtil.isNotBlank(query.getRealName()), SysUser::getRealName, query.getRealName())
                .eq(query.getStatus() != null, SysUser::getStatus, query.getStatus())
                .orderByDesc(SysUser::getCreateTime);

        IPage<SysUser> userPage = userMapper.selectPage(page, wrapper);

        Map<Long, String> roleNameMap = roleMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysRole::getId, SysRole::getRoleName));

        List<UserVO> records = userPage.getRecords().stream().map(user -> {
            UserVO vo = new UserVO();
            BeanUtil.copyProperties(user, vo);
            vo.setRoleName(roleNameMap.get(user.getRoleId()));
            return vo;
        }).collect(Collectors.toList());

        return PageResult.of(userPage.getTotal(), records);
    }

    @Override
    public void create(UserCreateDTO dto) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername()));
        if (count > 0) {
            throw new BusinessException("用户名已存在");
        }

        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPassword(SecureUtil.md5(dto.getPassword()));
        user.setRealName(dto.getRealName());
        user.setPhone(dto.getPhone());
        user.setRoleId(dto.getRoleId());
        user.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        userMapper.insert(user);
    }

    @Override
    public void update(UserUpdateDTO dto) {
        if (userMapper.selectById(dto.getId()) == null) {
            throw new BusinessException(404, "用户不存在");
        }

        SysUser update = new SysUser();
        update.setId(dto.getId());
        update.setRealName(dto.getRealName());
        update.setPhone(dto.getPhone());
        update.setRoleId(dto.getRoleId());
        update.setStatus(dto.getStatus());
        userMapper.updateById(update);
    }

    @Override
    public void delete(Long id) {
        if (id.equals(UserContext.getUserId())) {
            throw new BusinessException("不能删除当前登录用户");
        }
        if (userMapper.selectById(id) == null) {
            throw new BusinessException(404, "用户不存在");
        }
        userMapper.deleteById(id);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("状态值不合法");
        }
        if (id.equals(UserContext.getUserId()) && status == 0) {
            throw new BusinessException("不能禁用当前登录用户");
        }
        if (userMapper.selectById(id) == null) {
            throw new BusinessException(404, "用户不存在");
        }

        SysUser update = new SysUser();
        update.setId(id);
        update.setStatus(status);
        userMapper.updateById(update);
    }

    @Override
    public void resetPassword(Long id) {
        if (userMapper.selectById(id) == null) {
            throw new BusinessException(404, "用户不存在");
        }

        SysUser update = new SysUser();
        update.setId(id);
        update.setPassword(SecureUtil.md5("123456"));
        userMapper.updateById(update);
    }

    @Override
    public List<UserVO> listRepairers() {
        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, "REPAIRER"));
        if (role == null) {
            return Collections.emptyList();
        }
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getRoleId, role.getId())
                        .eq(SysUser::getStatus, 1)
                        .orderByAsc(SysUser::getId))
                .stream()
                .map(user -> {
                    UserVO vo = new UserVO();
                    BeanUtil.copyProperties(user, vo);
                    vo.setRoleName(role.getRoleName());
                    return vo;
                })
                .collect(Collectors.toList());
    }
}
