package com.demo.repair.vo;

import lombok.Data;

/**
 * 登录返回结果
 */
@Data
public class LoginVO {

    private String token;

    private Long userId;

    private String username;

    private String realName;

    private String roleCode;

    private String roleName;
}
