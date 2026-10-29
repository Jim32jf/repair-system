package com.demo.repair.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/**
 * 分页查询基础参数
 */
@Data
public class PageQuery {

    @Min(value = 1, message = "页码不能小于 1")
    private Integer pageNum = 1;

    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 100, message = "每页条数不能超过 100")
    private Integer pageSize = 10;
}
