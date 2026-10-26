package com.demo.repair.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单流转记录
 */
@Data
@TableName("order_log")
public class OrderLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long operatorId;

    private String action;

    private String remark;

    private LocalDateTime createTime;
}
