package com.demo.repair.common;

/**
 * 业务异常
 */
public class BusinessException extends RuntimeException {

    private final Integer code;

    public BusinessException(String message) {
        super(message);
        // 业务规则/参数类冲突默认 400；系统内部错误才走 500（由兜底 handler 处理）
        this.code = 400;
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }
}
