package com.byy.meterreading.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.byy.meterreading.cache.BusinessCacheService;
import com.byy.meterreading.dto.device.ResetDeviceSecretDTO;
import com.byy.meterreading.maintenance.RedisMaintenanceQueue;
import com.byy.meterreading.mapper.DeviceMapper;
import com.byy.meterreading.model.Device;
import com.byy.meterreading.service.DeviceCredentialService;
import com.byy.meterreading.service.DeviceHeartbeatService;
import com.byy.meterreading.service.DeviceMeterService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeviceRotationTest {
    private final DeviceMapper devices = mock(DeviceMapper.class);
    private final DeviceCredentialService credentials = mock(DeviceCredentialService.class);
    private final DeviceHeartbeatService heartbeat = mock(DeviceHeartbeatService.class);
    private final RedisMaintenanceQueue queue = mock(RedisMaintenanceQueue.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final DeviceServiceImpl service = new DeviceServiceImpl(devices, mock(DeviceMeterService.class),
            credentials, heartbeat, mock(BusinessCacheService.class), queue, jdbc);

    @BeforeAll
    static void metadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Device.class);
    }

    @Test
    void rotationDoesNotCallRedisInTheBusinessTransaction() {
        when(devices.selectForUpdate(1L)).thenReturn(Device.builder().id(1L).deviceNo("D1")
                .version(3).credentialVersion(2).build());
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), eq(1L), anyString()))
                .thenReturn(List.of());
        when(credentials.generate()).thenReturn(new DeviceCredentialService.GeneratedCredential("raw", "hash"));
        when(devices.update(isNull(), org.mockito.ArgumentMatchers.<Wrapper<Device>>any())).thenReturn(1);
        var response = service.resetDeviceSecret(7L, 1L, new ResetDeviceSecretDTO(3, UUID.randomUUID().toString()));
        assertEquals("raw", response.deviceSecret());
        assertEquals(3, response.credentialVersion());
        verify(queue).heartbeatBeforeVersion(1L, 3);
        verifyNoInteractions(heartbeat);
    }

    @Test
    void failedDatabaseUpdateCannotEnqueueCleanupOrReturnANewSecret() {
        when(devices.selectForUpdate(1L)).thenReturn(Device.builder().id(1L).version(3).credentialVersion(2).build());
        when(credentials.generate()).thenReturn(new DeviceCredentialService.GeneratedCredential("raw", "hash"));
        assertThrows(RuntimeException.class, () -> service.resetDeviceSecret(7L, 1L,
                new ResetDeviceSecretDTO(3, UUID.randomUUID().toString())));
        verifyNoInteractions(queue, heartbeat);
    }
}
