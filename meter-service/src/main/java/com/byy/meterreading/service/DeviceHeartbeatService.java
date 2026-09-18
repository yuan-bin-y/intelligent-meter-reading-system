package com.byy.meterreading.service;

/**
 * 设备心跳业务，负责记录认证设备的实时在线状态。
 */
public interface DeviceHeartbeatService {

    /**
     * 记录一次设备心跳，并刷新该设备在线状态的有效期。
     *
     * @param deviceId         设备数据库主键
     * @param deviceNo         设备编号
     * @param credentialVersion 本次认证所使用的设备凭证版本
     */
    void heartbeat(
            Long deviceId,
            String deviceNo,
            Integer credentialVersion
    );

    /**
     * 判断设备当前是否仍有有效的 Redis 心跳记录。
     *
     * @param deviceId 设备数据库主键
     * @return 心跳 Key 存在时返回 true，否则返回 false
     */
    boolean isOnline(Long deviceId);
}
