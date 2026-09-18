package com.byy.meterreading.service.impl;

import com.byy.meterreading.config.DeviceHeartbeatProperties;
import com.byy.meterreading.service.DeviceHeartbeatService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 使用 Redis TTL 记录设备实时在线状态。
 */
@Service
public class DeviceHeartbeatServiceImpl implements DeviceHeartbeatService {

    private static final String KEY_PREFIX = "meter:device:heartbeat:";

    /**
     * 原子写入心跳字段并刷新 TTL，避免只写入数据却未成功设置过期时间。
     */
    private static final DefaultRedisScript<Long> RECORD_HEARTBEAT_SCRIPT =
            new DefaultRedisScript<>("""
                    redis.call('HSET', KEYS[1], 'deviceId', ARGV[1])
                    redis.call('HSET', KEYS[1], 'deviceNo', ARGV[2])
                    redis.call('HSET', KEYS[1], 'credentialVersion', ARGV[3])
                    redis.call('HSET', KEYS[1], 'lastHeartbeatAt', ARGV[4])
                    redis.call('PEXPIRE', KEYS[1], ARGV[5])
                    return 1
                    """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final DeviceHeartbeatProperties properties;

    public DeviceHeartbeatServiceImpl(
            StringRedisTemplate redisTemplate,
            DeviceHeartbeatProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public void heartbeat(
            Long deviceId,
            String deviceNo,
            Integer credentialVersion
    ) {
        requireArguments(deviceId, deviceNo, credentialVersion);

        redisTemplate.execute(
                RECORD_HEARTBEAT_SCRIPT,
                List.of(heartbeatKey(deviceId)),
                deviceId.toString(),
                deviceNo,
                credentialVersion.toString(),
                Instant.now().toString(),
                Long.toString(properties.getTtl().toMillis())
        );
    }

    @Override
    public boolean isOnline(Long deviceId) {
        if (deviceId == null) {
            throw new IllegalArgumentException("deviceId 不能为空");
        }

        Boolean exists = redisTemplate.hasKey(heartbeatKey(deviceId));
        return Boolean.TRUE.equals(exists);
    }

    private void requireArguments(
            Long deviceId,
            String deviceNo,
            Integer credentialVersion
    ) {
        if (deviceId == null) {
            throw new IllegalArgumentException("deviceId 不能为空");
        }
        if (deviceNo == null || deviceNo.isBlank()) {
            throw new IllegalArgumentException("deviceNo 不能为空");
        }
        if (credentialVersion == null || credentialVersion < 0) {
            throw new IllegalArgumentException("credentialVersion 不合法");
        }
    }

    private String heartbeatKey(Long deviceId) {
        return KEY_PREFIX + deviceId;
    }
}
