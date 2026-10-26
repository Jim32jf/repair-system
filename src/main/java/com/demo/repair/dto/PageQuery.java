package com.demo.repair.dto;

import lombok.Data;

/**
 * 分页查询基础参数
 */
@Data
public class PageQuery {

    private Integer pageNum = 1;

    private Integer pageSize = 10;
}
