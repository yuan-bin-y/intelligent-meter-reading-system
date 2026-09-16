package com.byy.meterreading.vo.common;

import java.util.List;

/**
 * API 通用分页响应数据。
 *
 * @param records  当前页数据
 * @param total    符合条件的总记录数
 * @param page     当前页码，从 1 开始
 * @param pageSize 每页数量
 * @param <T>      列表元素类型
 */
public record PageVO<T>(
        List<T> records,
        long total,
        long page,
        long pageSize
) {

    public PageVO {
        records = records == null ? List.of() : List.copyOf(records);
    }
}
