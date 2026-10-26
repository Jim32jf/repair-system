package com.demo.repair.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 派单参数
 */
@Data
public class OrderAssignDTO {

    @NotNull(message = "工单ID不能为空")
    private Long orderId;

    @NotNull(message = "维修工ID不能为空")
    private Long repairerId;
}
