package com.byy.meterreading.auth.ai;

/**
 * AI 服务一次回调请求的签名凭证。
 *
 * @param timestamp  Unix 秒级时间戳
 * @param nonce      每次请求都不同的随机字符串
 * @param signature  HMAC-SHA256 十六进制签名
 * @param method     HTTP 方法
 * @param requestUri 不包含域名和查询参数的请求路径
 * @param bodySha256 请求体 SHA-256 十六进制摘要
 */
public record AiServiceRequestCredentials(
        String timestamp,
        String nonce,
        String signature,
        String method,
        String requestUri,
        String bodySha256
) {
}
