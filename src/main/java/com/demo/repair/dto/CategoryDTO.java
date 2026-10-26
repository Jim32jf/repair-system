package com.demo.repair.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 设备分类新增/修改参数
 */
@Data
public class CategoryDTO {

    private Long id;

    @NotBlank(message = "分类名称不能为空")
    private String name;

    private Integer sort;
}
