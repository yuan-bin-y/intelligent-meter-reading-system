package com.byy.meterreading.auth.ai;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/** 使用共享密钥、HMAC-SHA256 和 Redis 随机数校验 AI 服务回调。 */
@Component
public class AiServiceAuthenticationProvider
        implements AuthenticationProvider {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String NONCE_KEY_PREFIX = "auth:ai:nonce:";
    private static final String BAD_CREDENTIALS_MESSAGE =
            "AI服务认证凭证错误";
    private static final Pattern NONCE_PATTERN = Pattern.compile(
            "[A-Za-z0-9_-]{16,128}"
    );

    private final StringRedisTemplate redisTemplate;
    private final AiServiceProperties properties;
    private final SecretKeySpec signingKey;

    public AiServiceAuthenticationProvider(
            StringRedisTemplate redisTemplate,
            AiServiceProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.signingKey = createSigningKey(properties.secretBase64());
    }

    @Override
    public Authentication authenticate(Authentication authentication)
            throws AuthenticationException {
        if (!(authentication instanceof AiServiceAuthenticationToken token)) {
            return null;
        }

        try {
            String serviceId = requireServiceId(token.getPrincipal());
            AiServiceRequestCredentials credentials =
                    requireCredentials(token.getCredentials());
            validateTimestamp(credentials.timestamp());
            validateNonce(credentials.nonce());
            validateSignature(serviceId, credentials);
            reserveNonce(serviceId, credentials);

            AiServiceAuthenticationToken authenticated =
                    AiServiceAuthenticationToken.authenticated(
                            new AiServicePrincipal(serviceId)
                    );
            authenticated.setDetails(token.getDetails());
            return authenticated;
        } finally {
            token.eraseCredentials();
        }
    }

    @Override
    public boolean supports(Class<?> authenticationType) {
        return AiServiceAuthenticationToken.class
                .isAssignableFrom(authenticationType);
    }

    private String requireServiceId(Object principal) {
        if (!(principal instanceof String serviceId)
                || serviceId.isBlank()
                || !constantTimeEquals(
                properties.serviceId(), serviceId
        )) {
            throw new BadCredentialsException(BAD_CREDENTIALS_MESSAGE);
        }
        return serviceId;
    }

    private AiServiceRequestCredentials requireCredentials(
            Object credentials
    ) {
        if (!(credentials instanceof AiServiceRequestCredentials value)) {
            throw new BadCredentialsException(BAD_CREDENTIALS_MESSAGE);
        }
        if (isBlank(value.timestamp())
                || isBlank(value.nonce())
                || isBlank(value.signature())
                || isBlank(value.method())
                || isBlank(value.requestUri())
                || isBlank(value.bodySha256())) {
            throw new BadCredentialsException(BAD_CREDENTIALS_MESSAGE);
        }
        return value;
    }

    private void validateTimestamp(String timestampValue) {
        final Instant requestTime;
        try {
            requestTime = Instant.ofEpochSecond(
                    Long.parseLong(timestampValue)
            );
        } catch (NumberFormatException | DateTimeException exception) {
            throw new BadCredentialsException(
                    "AI服务请求时间戳不合法",
                    exception
            );
        }

        Instant now = Instant.now();
        if (requestTime.isBefore(
                now.minus(properties.allowedClockSkew())
        ) || requestTime.isAfter(
                now.plus(properties.allowedClockSkew())
        )) {
            throw new BadCredentialsException(
                    "AI服务请求已过期或服务器时间不同步"
            );
        }
    }

    private void validateNonce(String nonce) {
        if (!NONCE_PATTERN.matcher(nonce).matches()) {
            throw new BadCredentialsException(
                    "AI服务请求随机数格式不正确"
            );
        }
    }

    private void validateSignature(
            String serviceId,
            AiServiceRequestCredentials credentials
    ) {
        byte[] suppliedSignature;
        try {
            if (credentials.signature().length() != 64) {
                throw new IllegalArgumentException("签名长度错误");
            }
            suppliedSignature = HexFormat.of().parseHex(
                    credentials.signature()
            );
        } catch (IllegalArgumentException exception) {
            throw new BadCredentialsException(
                    BAD_CREDENTIALS_MESSAGE,
                    exception
            );
        }

        String canonicalRequest = canonicalRequest(
                serviceId,
                credentials
        );
        byte[] expectedSignature;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(signingKey);
            expectedSignature = mac.doFinal(
                    canonicalRequest.getBytes(StandardCharsets.UTF_8)
            );
        } catch (GeneralSecurityException exception) {
            throw new AuthenticationServiceException(
                    "AI服务签名校验器初始化失败",
                    exception
            );
        }

        if (!MessageDigest.isEqual(
                expectedSignature,
                suppliedSignature
        )) {
            throw new BadCredentialsException(BAD_CREDENTIALS_MESSAGE);
        }
    }

    /**
     * 签名原文各字段使用换行符连接，客户端必须严格使用相同顺序：
     * serviceId、method、requestUri、timestamp、nonce、bodySha256。
     */
    private String canonicalRequest(
            String serviceId,
            AiServiceRequestCredentials credentials
    ) {
        return String.join(
                "\n",
                serviceId,
                credentials.method(),
                credentials.requestUri(),
                credentials.timestamp(),
                credentials.nonce(),
                credentials.bodySha256()
        );
    }

    /** 签名通过后原子写入随机数；已经存在说明请求正在被重放。 */
    private void reserveNonce(
            String serviceId,
            AiServiceRequestCredentials credentials
    ) {
        String redisKey = NONCE_KEY_PREFIX
                + serviceId + ":" + credentials.nonce();
        try {
            Boolean reserved = redisTemplate.opsForValue().setIfAbsent(
                    redisKey,
                    credentials.timestamp(),
                    properties.nonceTtl()
            );
            if (!Boolean.TRUE.equals(reserved)) {
                throw new BadCredentialsException(
                        "AI服务请求已被处理，请勿重复提交"
                );
            }
        } catch (BadCredentialsException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            // 防重放依赖Redis；Redis不可用时拒绝认证，避免降级为可重放请求。
            throw new AuthenticationServiceException(
                    "AI服务防重放校验暂时不可用",
                    exception
            );
        }
    }

    private SecretKeySpec createSigningKey(String secretBase64) {
        final byte[] secret;
        try {
            secret = Base64.getDecoder().decode(secretBase64);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "AI服务认证密钥必须是合法Base64",
                    exception
            );
        }
        if (secret.length < 32) {
            throw new IllegalArgumentException(
                    "AI服务认证密钥解码后不能少于32字节"
            );
        }
        return new SecretKeySpec(secret, HMAC_ALGORITHM);
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
