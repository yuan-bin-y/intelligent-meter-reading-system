package com.byy.meterreading.maintenance;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/** 定期采样积压，Prometheus抓取时直接读取内存，不触发额外数据库查询。 */
@Component
public class ReliabilityMetrics {
    private static final Logger log = LoggerFactory.getLogger(ReliabilityMetrics.class);
    private final JdbcTemplate jdbc;
    private final AtomicLong cleanupPending = new AtomicLong();
    private final AtomicLong cleanupOldest = new AtomicLong();
    private final AtomicLong outboxPending = new AtomicLong();
    private final AtomicLong outboxFailed = new AtomicLong();
    private final AtomicLong outboxOldest = new AtomicLong();
    private final AtomicLong sampleUp = new AtomicLong();

    public ReliabilityMetrics(JdbcTemplate jdbc, MeterRegistry registry) {
        this.jdbc = jdbc;
        register(registry, "redis.maintenance.pending", cleanupPending);
        register(registry, "redis.maintenance.oldest.seconds", cleanupOldest);
        register(registry, "recognition.outbox.pending", outboxPending);
        register(registry, "recognition.outbox.failed", outboxFailed);
        register(registry, "recognition.outbox.oldest.seconds", outboxOldest);
        register(registry, "reliability.sample.up", sampleUp);
    }

    @Scheduled(fixedDelayString = "${app.reliability.sample-interval-ms:15000}", initialDelay = 15000)
    public void sample() {
        try {
            jdbc.query("""
                    SELECT COUNT(*), COALESCE(MAX(TIMESTAMPDIFF(SECOND, created_at, NOW())), 0)
                    FROM redis_maintenance_task WHERE status <> 'DONE'
                    """, rs -> {
                        if (rs.next()) {
                            cleanupPending.set(rs.getLong(1));
                            cleanupOldest.set(Math.max(0, rs.getLong(2)));
                        }
                        return null;
                    });
            jdbc.query("""
                    SELECT COALESCE(SUM(status IN ('PENDING','SENDING')),0),
                      COALESCE(SUM(status = 'FAILED'),0),
                      COALESCE(MAX(CASE WHEN status IN ('PENDING','SENDING')
                        THEN TIMESTAMPDIFF(SECOND, created_at, NOW()) END),0)
                    FROM mq_outbox_event WHERE status <> 'SENT'
                    """, rs -> {
                        if (rs.next()) {
                            outboxPending.set(rs.getLong(1));
                            outboxFailed.set(rs.getLong(2));
                            outboxOldest.set(Math.max(0, rs.getLong(3)));
                        }
                        return null;
                    });
            sampleUp.set(1);
        } catch (RuntimeException exception) {
            sampleUp.set(0);
            log.warn("可靠性指标采样失败", exception);
        }
    }

    private void register(MeterRegistry registry, String name, AtomicLong value) {
        Gauge.builder(name, value, AtomicLong::get).register(registry);
    }
}
