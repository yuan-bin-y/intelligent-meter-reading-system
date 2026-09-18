package com.byy.meterreading.service;

/**
 * 设备密钥生成、摘要和校验服务。
 * 明文密钥只允许在生成时短暂存在于内存中，不能保存到数据库或输出到日志。
 */
public interface DeviceCredentialService {

    /**
     * 生成高强度随机设备密钥及其 SHA-256 摘要。
     */
    GeneratedCredential generate();

    /**
     * 计算设备明文密钥的 SHA-256 十六进制摘要。
     */
    String hash(String rawSecret);

    /**
     * 使用常量时间比较判断明文密钥是否与数据库中的摘要匹配。
     */
    boolean matches(String rawSecret, String expectedHash);

    /**
     * 一次密钥生成结果；rawSecret 只用于本次接口响应，secretHash 用于数据库存储。
     */
    record GeneratedCredential(
            String rawSecret,
            String secretHash
    ) {
    }
}
