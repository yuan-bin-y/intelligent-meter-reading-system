package com.byy.meterreading.recognition.mq;

import com.byy.meterreading.mapper.MqOutboxEventMapper;
import com.byy.meterreading.model.MqOutboxEvent;
import com.byy.meterreading.model.enums.MqOutboxStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 定时发布 AI 识别任务的 Outbox 事件。
 *
 * <p>业务事务只负责将事件写入 mq_outbox_event。本任务随后查询已经到发送
 * 时间的事件，先通过条件更新原子抢占，再交给 RecognitionTaskPublisher
 * 发送到 RabbitMQ。这样既不会在业务事务中直接依赖 RabbitMQ，也能支持
 * 消息失败重试和多应用实例并发执行。</p>
 */
@Component
public class OutboxEventPublisherJob {

    private static final Logger log = LoggerFactory.getLogger(
            OutboxEventPublisherJob.class
    );

    /** mq_outbox_event.last_error 字段允许保存的最大字符数。 */
    private static final int MAX_ERROR_LENGTH = 1000;

    private final MqOutboxEventMapper outboxEventMapper;
    private final RecognitionTaskPublisher taskPublisher;
    private final int batchSize;
    private final Duration lockTimeout;
    private final Duration retryBaseDelay;
    private final Duration retryMaxDelay;
    private final String publisherInstanceId;

    public OutboxEventPublisherJob(
            MqOutboxEventMapper outboxEventMapper,
            RecognitionTaskPublisher taskPublisher,
            @Value("${app.recognition.mq.outbox-batch-size:50}")
            int batchSize,
            @Value("${app.recognition.mq.outbox-lock-timeout:PT1M}")
            Duration lockTimeout,
            @Value("${app.recognition.mq.outbox-retry-base-delay:PT5S}")
            Duration retryBaseDelay,
            @Value("${app.recognition.mq.outbox-retry-max-delay:PT5M}")
            Duration retryMaxDelay,
            @Value("${spring.application.name:meter-reading-system}")
            String applicationName
    ) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                    "Outbox每批发送数量必须大于0"
            );
        }
        requirePositiveDuration(lockTimeout, "Outbox锁超时时间");
        requirePositiveDuration(retryBaseDelay, "Outbox重试基础延迟");
        requirePositiveDuration(retryMaxDelay, "Outbox最大重试延迟");
        if (retryBaseDelay.compareTo(retryMaxDelay) > 0) {
            throw new IllegalArgumentException(
                    "Outbox重试基础延迟不能大于最大重试延迟"
            );
        }

        this.outboxEventMapper = outboxEventMapper;
        this.taskPublisher = taskPublisher;
        this.batchSize = batchSize;
        this.lockTimeout = lockTimeout;
        this.retryBaseDelay = retryBaseDelay;
        this.retryMaxDelay = retryMaxDelay;
        this.publisherInstanceId = applicationName + "-" + UUID.randomUUID();
    }

    /**
     * 上一轮结束后等待指定间隔，再执行下一轮 Outbox 发布。
     *
     * <p>该方法不使用事务包住整批数据。每条消息先通过 claimEvent 的条件
     * UPDATE 抢占，之后才等待 RabbitMQ Confirm，避免等待消息确认期间一直
     * 占用数据库事务和行锁。</p>
     */
    @Scheduled(
            fixedDelayString =
                    "${app.recognition.mq.outbox-publish-interval:3000}"
    )
    public void publishPendingEvents() {
        LocalDateTime now = LocalDateTime.now();
        recoverExpiredClaims(now);

        List<MqOutboxEvent> events =
                outboxEventMapper.selectPublishableEvents(now, batchSize);
        for (MqOutboxEvent event : events) {
            publishOne(event);
        }
    }

    /**
     * 应用在发送过程中崩溃时，事件可能一直停留在 SENDING。
     * 超过锁定时间后将其恢复为 PENDING，让当前或其他实例重新发送。
     */
    private void recoverExpiredClaims(LocalDateTime now) {
        LocalDateTime lockExpiredBefore = now.minus(lockTimeout);
        int recovered = outboxEventMapper.resetExpiredSendingEvents(
                lockExpiredBefore,
                now,
                "Outbox发送实例超时，已释放锁并等待重新发送",
                now
        );
        if (recovered > 0) {
            log.warn("已恢复 {} 条超时的Outbox发送任务", recovered);
        }
    }

    /** 抢占并发送一条事件；没有抢到说明已被另一个应用实例处理。 */
    private void publishOne(MqOutboxEvent event) {
        if (event == null
                || event.getId() == null
                || event.getVersion() == null) {
            log.error("忽略缺少主键或版本号的Outbox事件");
            return;
        }

        LocalDateTime lockedAt = LocalDateTime.now();
        int claimed = outboxEventMapper.claimEvent(
                event.getId(),
                event.getVersion(),
                publisherInstanceId,
                lockedAt
        );
        if (claimed != 1) {
            return;
        }

        // claimEvent 已在数据库中把状态和版本分别改为 SENDING、version + 1。
        event.setStatus(MqOutboxStatus.SENDING.name());
        event.setLockedBy(publisherInstanceId);
        event.setLockedAt(lockedAt);
        event.setVersion(event.getVersion() + 1);

        try {
            taskPublisher.publish(event);
            markSent(event);
        } catch (Exception exception) {
            handlePublishFailure(event, exception);
        }
    }

    /** Broker Confirm 成功后才把数据库事件标记为 SENT。 */
    private void markSent(MqOutboxEvent event) {
        int updated = outboxEventMapper.markSent(
                event.getId(),
                event.getVersion(),
                LocalDateTime.now()
        );
        if (updated != 1) {
            /*
             * 消息可能已经进入 RabbitMQ，但状态更新失败。锁超时后该事件会被
             * 再次发送，因此消费端必须使用 eventId 做幂等处理。
             */
            log.error(
                    "RabbitMQ已确认消息，但Outbox状态更新失败：id={}, eventId={}",
                    event.getId(),
                    event.getEventId()
            );
        }
    }

    /**
     * 发布失败后增加失败次数。达到最大次数时标记 FAILED；否则按指数退避
     * 重新设为 PENDING，等待下次定时任务继续发送。
     */
    private void handlePublishFailure(
            MqOutboxEvent event,
            Exception exception
    ) {
        int currentRetryCount = event.getRetryCount() == null
                ? 0 : event.getRetryCount();
        int maxRetryCount = event.getMaxRetryCount() == null
                ? 0 : event.getMaxRetryCount();
        int nextRetryCount = currentRetryCount + 1;
        LocalDateTime failedAt = LocalDateTime.now();
        String lastError = errorMessage(exception);

        if (nextRetryCount >= maxRetryCount) {
            int updated = outboxEventMapper.markFailed(
                    event.getId(),
                    event.getVersion(),
                    nextRetryCount,
                    lastError,
                    failedAt
            );
            if (updated == 1) {
                log.error(
                        "Outbox消息发送达到最大重试次数：id={}, eventId={}, "
                                + "retryCount={}, error={}",
                        event.getId(), event.getEventId(),
                        nextRetryCount, lastError
                );
            } else {
                log.error(
                        "Outbox消息发送失败，且FAILED状态更新失败：id={}, "
                                + "eventId={}, error={}",
                        event.getId(), event.getEventId(), lastError
                );
            }
            return;
        }

        Duration retryDelay = calculateRetryDelay(nextRetryCount);
        LocalDateTime nextRetryAt = failedAt.plus(retryDelay);
        int updated = outboxEventMapper.markRetry(
                event.getId(),
                event.getVersion(),
                nextRetryCount,
                nextRetryAt,
                lastError,
                failedAt
        );
        if (updated == 1) {
            log.warn(
                    "Outbox消息发送失败，等待重试：id={}, eventId={}, "
                            + "retryCount={}, nextRetryAt={}, error={}",
                    event.getId(), event.getEventId(), nextRetryCount,
                    nextRetryAt, lastError
            );
        } else {
            log.error(
                    "Outbox消息发送失败，且重试状态更新失败：id={}, "
                            + "eventId={}, error={}",
                    event.getId(), event.getEventId(), lastError
            );
        }
    }

    /**
     * 第一次失败等待基础时间，之后按 2 倍增长，并限制在最大重试延迟内。
     */
    private Duration calculateRetryDelay(int retryCount) {
        int exponent = Math.max(0, Math.min(retryCount - 1, 30));
        long multiplier = 1L << exponent;
        long baseMillis = retryBaseDelay.toMillis();
        long maxMillis = retryMaxDelay.toMillis();
        long delayMillis;
        try {
            delayMillis = Math.multiplyExact(baseMillis, multiplier);
        } catch (ArithmeticException exception) {
            delayMillis = maxMillis;
        }
        return Duration.ofMillis(Math.min(delayMillis, maxMillis));
    }

    /** 保存适合排查问题的异常信息，并防止超过数据库字段长度。 */
    private String errorMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        } else {
            message = exception.getClass().getSimpleName() + ": " + message;
        }
        return message.length() <= MAX_ERROR_LENGTH
                ? message : message.substring(0, MAX_ERROR_LENGTH);
    }

    private static void requirePositiveDuration(
            Duration duration,
            String propertyName
    ) {
        if (duration == null
                || duration.isZero()
                || duration.isNegative()) {
            throw new IllegalArgumentException(
                    propertyName + "必须大于0"
            );
        }
    }
}
