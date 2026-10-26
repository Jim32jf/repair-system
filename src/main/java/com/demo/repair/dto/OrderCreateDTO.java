package com.demo.repair.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 提交报修参数
 */
@Data
public class OrderCreateDTO {

    @NotNull(message = "设备分类不能为空")
    private Long categoryId;

    @NotBlank(message = "设备名称不能为空")
    private String deviceName;

    @NotBlank(message = "故障地点不能为空")
    private String location;

    private String description;

    private String imageUrl;
}
