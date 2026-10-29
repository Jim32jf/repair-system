package com.demo.repair.vo;

import lombok.Data;

/**
 * 设备分类返回对象（不含 deleted、时间戳等内部字段）
 */
@Data
public class CategoryVO {

    private Long id;

    private String name;

    private Integer sort;
}
