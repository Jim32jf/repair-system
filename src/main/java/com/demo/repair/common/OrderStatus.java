package com.demo.repair.common;

/**
 * 工单状态常量
 */
public final class OrderStatus {

    /** 待派单 */
    public static final int PENDING_ASSIGN = 0;

    /** 已派单 */
    public static final int ASSIGNED = 1;

    /** 处理中 */
    public static final int PROCESSING = 2;

    /** 已完成 */
    public static final int FINISHED = 3;

    /** 已评价 */
    public static final int EVALUATED = 4;

    private OrderStatus() {
    }

    public static String name(Integer status) {
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case PENDING_ASSIGN:
                return "待派单";
            case ASSIGNED:
                return "已派单";
            case PROCESSING:
                return "处理中";
            case FINISHED:
                return "已完成";
            case EVALUATED:
                return "已评价";
            default:
                return "未知";
        }
    }
}
