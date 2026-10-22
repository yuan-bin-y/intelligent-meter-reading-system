package com.byy.meterreading.service.impl;

import com.byy.meterreading.cache.BusinessCacheService;
import com.byy.meterreading.config.DeviceHeartbeatProperties;
import com.byy.meterreading.dto.device.ResetDeviceSecretDTO;
import com.byy.meterreading.maintenance.RedisMaintenanceJob;
import com.byy.meterreading.maintenance.RedisMaintenanceQueue;
import com.byy.meterreading.service.DeviceHeartbeatService;
import com.byy.meterreading.service.DeviceMeterService;
import com.byy.meterreading.support.IntegrationInfrastructure;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

@EnabledIfEnvironmentVariable(named = "METER_IT", matches = "true")
class DeviceRotationIntegrationTest {
    private static IntegrationInfrastructure infra;

    @BeforeAll
    static void setUp() throws Exception {
        infra = new IntegrationInfrastructure();
    }

    @AfterAll
    static void close() {
        infra.close();
    }

    @Test
    void redisFailureDoesNotLoseCommittedRotationAndSameRequestDoesNotRotateAgain() {
        long id = Math.abs(UUID.randomUUID().getMostSignificantBits() >> 16);
        infra.jdbc.update("""
                INSERT INTO device(id, device_no, device_name, device_type, status, version,
                  credential_version, created_by, updated_by, secret_hash, secret_rotated_at)
                VALUES (?, ?, 'integration', 'CAMERA', 1, 0, 1, 1, 1, REPEAT('a', 64), NOW())
                """, id, "IT-" + id);
        var target = new DeviceServiceImpl(infra.devices, mock(DeviceMeterService.class),
                new DeviceCredentialServiceImpl(), mock(DeviceHeartbeatService.class),
                mock(BusinessCacheService.class), new RedisMaintenanceQueue(infra.jdbc), infra.jdbc);
        var proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(infra.dataSource),
                new AnnotationTransactionAttributeSource()));
        var service = (DeviceServiceImpl) proxy.getProxy();
        var request = new ResetDeviceSecretDTO(0, UUID.randomUUID().toString());
        var first = service.resetDeviceSecret(1L, id, request);
        assertFalse(first.replayed());
        assertTrue(new DeviceCredentialServiceImpl().matches(first.deviceSecret(),
                infra.devices.selectById(id).getSecretHash()));
        var replay = service.resetDeviceSecret(1L, id, request);
        assertTrue(replay.replayed());
        assertNull(replay.deviceSecret());
        assertEquals(1, infra.devices.selectById(id).getVersion());
        assertThrows(RuntimeException.class, () -> service.resetDeviceSecret(2L, id, request));

        var unavailable = mock(DeviceHeartbeatService.class);
        doThrow(new IllegalStateException("injected Redis outage")).when(unavailable)
                .clearHeartbeatBeforeVersion(anyLong(), anyInt());
        new RedisMaintenanceJob(infra.jdbc, unavailable).drain();
        assertEquals("PENDING", infra.jdbc.queryForObject(
                "SELECT status FROM redis_maintenance_task WHERE resource_key = ?", String.class, Long.toString(id)));
        infra.jdbc.update("UPDATE redis_maintenance_task SET next_attempt_at = NOW() WHERE resource_key = ?",
                Long.toString(id));
        var heartbeat = new DeviceHeartbeatServiceImpl(infra.redis, new DeviceHeartbeatProperties());
        heartbeat.heartbeat(id, "IT-" + id, 2);
        new RedisMaintenanceJob(infra.jdbc, heartbeat).drain();
        assertTrue(heartbeat.isOnline(id, 2)); // 重试清理不能删除新凭证产生的心跳。
        heartbeat.heartbeat(id, "IT-" + id, 1);
        assertTrue(heartbeat.isOnline(id, 2)); // 在途旧请求不能覆盖新心跳。
        assertFalse(heartbeat.isOnline(id, 1));
        infra.redis.delete("meter:device:heartbeat:" + id);
    }
}
