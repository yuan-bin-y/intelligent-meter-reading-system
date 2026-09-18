package com.byy.meterreading.service.impl;

import com.byy.meterreading.service.DeviceCredentialService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 基于安全随机数和 SHA-256 的设备密钥服务实现。
 */
@Service
public class DeviceCredentialServiceImpl
        implements DeviceCredentialService {

    private static final int SECRET_BYTE_LENGTH = 32;
    private static final int SHA_256_HEX_LENGTH = 64;
    private static final String HASH_ALGORITHM = "SHA-256";

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 生成 256 位随机密钥；Base64 URL 编码不带填充，便于通过请求头传递。
     */
    @Override
    public GeneratedCredential generate() {
        byte[] secretBytes = new byte[SECRET_BYTE_LENGTH];
        secureRandom.nextBytes(secretBytes);
        String rawSecret = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(secretBytes);
        return new GeneratedCredential(rawSecret, hash(rawSecret));
    }

    @Override
    public String hash(String rawSecret) {
        if (rawSecret == null || rawSecret.isBlank()) {
            throw new IllegalArgumentException("设备密钥不能为空");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hashBytes = digest.digest(
                    rawSecret.getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "当前JDK不支持SHA-256算法",
                    exception
            );
        }
    }

    /**
     * 摘要格式非法时直接认证失败，合法摘要使用常量时间比较降低时序攻击风险。
     */
    @Override
    public boolean matches(String rawSecret, String expectedHash) {
        if (rawSecret == null
                || rawSecret.isBlank()
                || !isValidSha256Hash(expectedHash)) {
            return false;
        }

        byte[] actualBytes = hash(rawSecret).getBytes(StandardCharsets.US_ASCII);
        byte[] expectedBytes = expectedHash.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(actualBytes, expectedBytes);
    }

    private boolean isValidSha256Hash(String value) {
        if (value == null || value.length() != SHA_256_HEX_LENGTH) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            boolean decimal = character >= '0' && character <= '9';
            boolean lowercaseHex = character >= 'a' && character <= 'f';
            if (!decimal && !lowercaseHex) {
                return false;
            }
        }
        return true;
    }
}
