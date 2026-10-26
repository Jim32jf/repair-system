package com.demo.repair.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单流转记录
 */
@Data
public class OrderLogVO {

    private Long id;

    private Long operatorId;

    private String operatorName;

    private String action;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
