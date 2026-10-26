package com.demo.repair.service;

import com.demo.repair.common.PageResult;
import com.demo.repair.dto.UserCreateDTO;
import com.demo.repair.dto.UserQueryDTO;
import com.demo.repair.dto.UserUpdateDTO;
import com.demo.repair.vo.UserVO;

import java.util.List;

public interface UserService {

    PageResult<UserVO> page(UserQueryDTO query);

    void create(UserCreateDTO dto);

    void update(UserUpdateDTO dto);

    void delete(Long id);

    void updateStatus(Long id, Integer status);

    void resetPassword(Long id);

    List<UserVO> listRepairers();
}
