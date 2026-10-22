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
     * 核对心跳版本与数据库中的当前凭证版本。
     *
     * @param deviceId 设备数据库主键
     * @return 心跳未过期且凭证版本一致时返回true
     */
    boolean isOnline(Long deviceId, Integer credentialVersion);

    /**
     * 清理低于指定版本的心跳；后台重试不能删除新凭证产生的心跳。
     *
     * @param deviceId 设备数据库主键
     */
    void clearHeartbeatBeforeVersion(Long deviceId, int credentialVersion);
}
