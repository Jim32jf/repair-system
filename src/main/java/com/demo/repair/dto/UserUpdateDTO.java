package com.demo.repair.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

/**
 * 修改用户参数
 */
@Data
public class UserUpdateDTO {

    @NotNull(message = "用户ID不能为空")
    private Long id;

    private String realName;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    private Long roleId;

    private Integer status;
}
