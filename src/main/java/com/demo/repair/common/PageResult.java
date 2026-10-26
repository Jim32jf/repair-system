package com.demo.repair.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.List;

/**
 * 分页返回结果
 */
@Data
public class PageResult<T> {

    private Long total;

    private List<T> records;

    public static <T> PageResult<T> of(IPage<T> page) {
        return of(page.getTotal(), page.getRecords());
    }

    public static <T> PageResult<T> of(Long total, List<T> records) {
        PageResult<T> result = new PageResult<>();
        result.setTotal(total);
        result.setRecords(records);
        return result;
    }
}
