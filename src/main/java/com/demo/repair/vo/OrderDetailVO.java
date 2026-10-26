package com.demo.repair.vo;

import com.demo.repair.entity.Evaluation;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 工单详情：基础信息 + 流转记录 + 评价
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderDetailVO extends OrderVO {

    private List<OrderLogVO> logs;

    private Evaluation evaluation;
}
