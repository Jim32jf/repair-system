package com.demo.repair.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 报修工单
 */
@Data
@TableName("repair_order")
public class RepairOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long studentId;

    private Long categoryId;

    private String deviceName;

    private String location;

    private String description;

    private String imageUrl;

    /** 0待派单 1已派单 2处理中 3已完成 4已评价 */
    private Integer status;

    private Long repairerId;

    private LocalDateTime finishTime;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
