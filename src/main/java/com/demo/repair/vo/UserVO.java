package com.demo.repair.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户列表返回对象（不含密码）
 */
@Data
public class UserVO {

    private Long id;

    private String username;

    private String realName;

    private String phone;

    private Long roleId;

    private String roleName;

    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
