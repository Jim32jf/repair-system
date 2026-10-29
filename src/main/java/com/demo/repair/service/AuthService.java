package com.demo.repair.service;

import com.demo.repair.dto.LoginDTO;
import com.demo.repair.vo.LoginVO;

public interface AuthService {

    /**
     * 登录，返回 token 和用户信息
     */
    LoginVO login(LoginDTO dto);

    /**
     * 登出：吊销当前登录用户此前签发的全部 token
     */
    void logout();
}
