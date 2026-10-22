package com.byy.meterreading.maintenance;

import com.byy.meterreading.common.trace.TraceIdContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 清理任务必须与业务更新一起落库；Redis 失败不能改变已提交的业务结果。 */
@Component
public class RedisMaintenanceQueue {
    private final JdbcTemplate jdbc;

    public RedisMaintenanceQueue(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void heartbeatBeforeVersion(Long deviceId, int version) {
        enqueue("HEARTBEAT", deviceId.toString(), version);
    }

    public void invalidateCache(String key) {
        enqueue("CACHE", key, 0);
    }

    private void enqueue(String type, String key, int version) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Redis清理任务必须在业务事务内保存");
        }
        jdbc.update("""
                INSERT INTO redis_maintenance_task
                  (task_type, resource_key, target_version, trace_id)
                VALUES (?, ?, ?, ?)
                """, type, key, version, TraceIdContext.getOrCreate());
    }
}
