package com.demo.repair.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单列表返回对象
 */
@Data
public class OrderVO {

    private Long id;

    private String orderNo;

    private Long studentId;

    private String studentName;

    private Long categoryId;

    private String categoryName;

    private String deviceName;

    private String location;

    private String description;

    private String imageUrl;

    private Integer status;

    private String statusName;

    private Long repairerId;

    private String repairerName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finishTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
