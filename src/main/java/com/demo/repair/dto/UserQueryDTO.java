package com.demo.repair.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户分页查询条件
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserQueryDTO extends PageQuery {

    private String username;

    private String realName;

    private Integer status;
}
