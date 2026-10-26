package com.demo.repair.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前登录用户信息（来自 JWT）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser {

    private Long userId;

    private String username;

    private String roleCode;
}
