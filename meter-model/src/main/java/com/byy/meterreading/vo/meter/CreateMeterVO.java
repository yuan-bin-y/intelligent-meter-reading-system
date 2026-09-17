package com.byy.meterreading.vo.meter;

/**
 * 新增表具成功后的响应数据。
 *
 * @param meterId 数据库生成的表具主键
 * @param meterNo 全局唯一的表具编号
 */
public record CreateMeterVO(
        Long meterId,
        String meterNo
) {
}
