package com.demo.repair.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工单分页查询条件
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderQueryDTO extends PageQuery {

    private Integer status;

    /** 工单号 / 设备名称 / 地点 模糊搜索 */
    private String keyword;
}
