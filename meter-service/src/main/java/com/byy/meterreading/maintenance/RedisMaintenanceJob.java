package com.byy.meterreading.maintenance;

import com.byy.meterreading.common.trace.TraceIdContext;
import com.byy.meterreading.service.DeviceHeartbeatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** 每次最多处理50条；领取租约过期后可由其他实例接手，完成操作必须核对令牌。 */
@Component
public class RedisMaintenanceJob {
    private static final Logger log = LoggerFactory.getLogger(RedisMaintenanceJob.class);
    private final JdbcTemplate jdbc;
    private final DeviceHeartbeatService heartbeatService;

    public RedisMaintenanceJob(JdbcTemplate jdbc, DeviceHeartbeatService heartbeatService) {
        this.jdbc = jdbc;
        this.heartbeatService = heartbeatService;
    }

    @Scheduled(fixedDelayString = "${app.redis-maintenance.interval-ms:5000}")
    public void drain() {
        var tasks = jdbc.query("""
                SELECT id, task_type, resource_key, target_version, attempts, trace_id
                FROM redis_maintenance_task
                WHERE (status = 'PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP(6))
                   OR (status = 'PROCESSING' AND lease_until < CURRENT_TIMESTAMP(6))
                ORDER BY id LIMIT 50
                """, (rs, row) -> new Task(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getInt(4), rs.getInt(5), rs.getString(6)));
        for (Task task : tasks) {
            process(task);
        }
        // 完成记录保留7天，未完成记录一直保留，供告警和人工排查。
        jdbc.update("""
                DELETE FROM redis_maintenance_task
                WHERE status = 'DONE' AND updated_at < CURRENT_TIMESTAMP - INTERVAL 7 DAY LIMIT 500
                """);
    }

    private void process(Task task) {
        String token = UUID.randomUUID().toString();
        int claimed = jdbc.update("""
                UPDATE redis_maintenance_task SET status = 'PROCESSING', lease_token = ?,
                  lease_until = CURRENT_TIMESTAMP(6) + INTERVAL 60 SECOND, attempts = attempts + 1
                WHERE id = ? AND ((status = 'PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP(6))
                   OR (status = 'PROCESSING' AND lease_until < CURRENT_TIMESTAMP(6)))
                """, token, task.id());
        if (claimed != 1) {
            return;
        }
        try (var ignored = TraceIdContext.open(task.traceId())) {
            try {
                if (!"HEARTBEAT".equals(task.type())) {
                    throw new IllegalStateException("未知Redis清理类型");
                }
                heartbeatService.clearHeartbeatBeforeVersion(Long.valueOf(task.key()), task.version());
                jdbc.update("""
                        UPDATE redis_maintenance_task SET status = 'DONE', lease_until = NULL,
                          last_error = NULL WHERE id = ? AND lease_token = ?
                        """, task.id(), token);
            } catch (RuntimeException exception) {
                int delay = (int) Math.min(300, 5L << Math.min(task.attempts(), 6));
                jdbc.update("""
                        UPDATE redis_maintenance_task SET status = 'PENDING', lease_until = NULL,
                          next_attempt_at = TIMESTAMPADD(SECOND, ?, CURRENT_TIMESTAMP(6)), last_error = ?
                        WHERE id = ? AND lease_token = ?
                        """, delay, exception.getClass().getSimpleName(), task.id(), token);
                log.warn("Redis清理失败，任务保留并稍后重试，taskId={}", task.id(), exception);
            }
        }
    }

    private record Task(long id, String type, String key, int version, int attempts, String traceId) {
    }
}
