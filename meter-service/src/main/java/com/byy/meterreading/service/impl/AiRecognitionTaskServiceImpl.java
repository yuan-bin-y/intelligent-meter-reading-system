package com.byy.meterreading.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byy.meterreading.common.exception.ResourceConflictException;
import com.byy.meterreading.common.exception.ResourceNotFoundException;
import com.byy.meterreading.common.exception.VersionConflictException;
import com.byy.meterreading.dto.airecognition.AiRecognitionTaskPageQueryDTO;
import com.byy.meterreading.dto.airecognition.CancelAiRecognitionTaskDTO;
import com.byy.meterreading.dto.airecognition.CompleteAiRecognitionTaskDTO;
import com.byy.meterreading.dto.airecognition.FailAiRecognitionTaskDTO;
import com.byy.meterreading.dto.airecognition.RetryAiRecognitionTaskDTO;
import com.byy.meterreading.dto.airecognition.StartAiRecognitionTaskDTO;
import com.byy.meterreading.mapper.AiRecognitionTaskMapper;
import com.byy.meterreading.mapper.MeterImageMapper;
import com.byy.meterreading.mapper.MeterReadingResultImageMapper;
import com.byy.meterreading.mapper.MeterReadingResultMapper;
import com.byy.meterreading.mapper.MeterReadingTaskMapper;
import com.byy.meterreading.mapper.MqOutboxEventMapper;
import com.byy.meterreading.mapper.projection.AiRecognitionTaskDetailRow;
import com.byy.meterreading.mapper.projection.AiRecognitionTaskListRow;
import com.byy.meterreading.model.AiRecognitionTask;
import com.byy.meterreading.model.MeterImage;
import com.byy.meterreading.model.MeterReadingResult;
import com.byy.meterreading.model.MeterReadingResultImage;
import com.byy.meterreading.model.MeterReadingTask;
import com.byy.meterreading.model.MqOutboxEvent;
import com.byy.meterreading.model.enums.AiRecognitionStatus;
import com.byy.meterreading.model.enums.MeterImageStatus;
import com.byy.meterreading.model.enums.MeterImageStorageStatus;
import com.byy.meterreading.model.enums.MeterImageType;
import com.byy.meterreading.model.enums.MeterReadingReviewStatus;
import com.byy.meterreading.model.enums.MeterReadingTaskStatus;
import com.byy.meterreading.model.enums.MqOutboxStatus;
import com.byy.meterreading.model.enums.TaskExecutorType;
import com.byy.meterreading.service.AiRecognitionTaskService;
import com.byy.meterreading.vo.airecognition.AiRecognitionTaskActionVO;
import com.byy.meterreading.vo.airecognition.AiRecognitionTaskDetailVO;
import com.byy.meterreading.vo.airecognition.AiRecognitionTaskListVO;
import com.byy.meterreading.vo.common.PageVO;
import org.jspecify.annotations.NonNull;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * AI 识别任务状态机、Outbox 落库和识别结果跨表事务实现。
 *
 * <p>该实现只把待发送消息写入 mq_outbox_event，不直接调用 RabbitMQ；
 * 后续由独立的 Outbox 发布任务读取并发送。</p>
 */
@Service
public class AiRecognitionTaskServiceImpl
        implements AiRecognitionTaskService {

    private static final String RECOGNITION_NO_PREFIX = "AIR-";
    private static final String AGGREGATE_TYPE = "AI_RECOGNITION_TASK";
    private static final String EVENT_TYPE = "RECOGNITION_TASK_CREATED";
    private static final String EXCHANGE_NAME = "meter.recognition.exchange";
    private static final String ROUTING_KEY = "meter.recognition.task";

    private final AiRecognitionTaskMapper recognitionTaskMapper;
    private final MqOutboxEventMapper outboxEventMapper;
    private final MeterImageMapper imageMapper;
    private final MeterReadingTaskMapper readingTaskMapper;
    private final MeterReadingResultMapper readingResultMapper;
    private final MeterReadingResultImageMapper resultImageMapper;
    private final ObjectMapper objectMapper;

    public AiRecognitionTaskServiceImpl(
            AiRecognitionTaskMapper recognitionTaskMapper,
            MqOutboxEventMapper outboxEventMapper,
            MeterImageMapper imageMapper,
            MeterReadingTaskMapper readingTaskMapper,
            MeterReadingResultMapper readingResultMapper,
            MeterReadingResultImageMapper resultImageMapper,
            ObjectMapper objectMapper
    ) {
        this.recognitionTaskMapper = recognitionTaskMapper;
        this.outboxEventMapper = outboxEventMapper;
        this.imageMapper = imageMapper;
        this.readingTaskMapper = readingTaskMapper;
        this.readingResultMapper = readingResultMapper;
        this.resultImageMapper = resultImageMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 设备上传图片成功后自动创建识别任务；任务与 Outbox 同事务提交。
     */
    @Override
    @Transactional
    public AiRecognitionTaskActionVO createAutomaticTask(Long imageId) {
        return createTask(imageId);
    }

    /** 管理员手动创建使用相同的校验和 Outbox 写入流程。 */
    @Override
    @Transactional
    public AiRecognitionTaskActionVO createManualTask(
            Long adminId,
            Long imageId
    ) {
        requirePositiveId(adminId, "管理员ID不合法");
        return createTask(imageId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageVO<AiRecognitionTaskListVO> listAdminTasks(
            AiRecognitionTaskPageQueryDTO queryDTO
    ) {
        validateCreatedRange(queryDTO);
        Page<AiRecognitionTaskListRow> page = new Page<>(
                queryDTO.page(), queryDTO.pageSize()
        );
        IPage<AiRecognitionTaskListRow> result =
                recognitionTaskMapper.selectAdminPage(
                        page,
                        queryDTO.recognitionNo(),
                        queryDTO.readingTaskNo(),
                        queryDTO.meterNo(),
                        enumName(queryDTO.status()),
                        queryDTO.modelName(),
                        queryDTO.createdAtStart(),
                        queryDTO.createdAtEnd()
                );
        return new PageVO<>(
                result.getRecords().stream().map(this::toListVO).toList(),
                result.getTotal(), result.getCurrent(), result.getSize()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public AiRecognitionTaskDetailVO getAdminTask(
            Long recognitionTaskId
    ) {
        requirePositiveId(recognitionTaskId, "识别任务ID必须大于0");
        AiRecognitionTaskDetailRow row =
                recognitionTaskMapper.selectAdminDetail(recognitionTaskId);
        if (row == null) {
            throw new ResourceNotFoundException("AI识别任务不存在");
        }
        return toDetailVO(row);
    }

    /**
     * 重试复用原识别任务，增加 retryCount，并产生新的 Outbox 事件。
     */
    @Override
    @Transactional
    public AiRecognitionTaskActionVO retryTask(
            Long adminId,
            Long recognitionTaskId,
            RetryAiRecognitionTaskDTO retryDTO
    ) {
        requirePositiveId(adminId, "管理员ID不合法");
        AiRecognitionTask task = requireTaskForUpdate(recognitionTaskId);
        requireVersion(retryDTO.version(), task.getVersion());

        AiRecognitionStatus currentStatus = statusOf(task);
        requireTransition(currentStatus, AiRecognitionStatus.PENDING);
        if (task.getRetryCount() >= task.getMaxRetryCount()) {
            throw new IllegalArgumentException("AI识别任务已达到最大重试次数");
        }

        MeterReadingTask readingTask =
                readingTaskMapper.selectByIdForUpdate(task.getReadingTaskId());
        if (readingTask == null) {
            throw new ResourceNotFoundException("关联抄表任务不存在");
        }
        restoreReadingTaskForRetry(readingTask, adminId);

        int updated = recognitionTaskMapper.update(
                null,
                Wrappers.<AiRecognitionTask>lambdaUpdate()
                        .set(AiRecognitionTask::getStatus,
                                AiRecognitionStatus.PENDING.name())
                        .set(AiRecognitionTask::getResultId, null)
                        .set(AiRecognitionTask::getRecognizedValue, null)
                        .set(AiRecognitionTask::getConfidence, null)
                        .set(AiRecognitionTask::getModelName, null)
                        .set(AiRecognitionTask::getModelVersion, null)
                        .set(AiRecognitionTask::getRawResult, null)
                        .set(AiRecognitionTask::getFailureCode, null)
                        .set(AiRecognitionTask::getFailureMessage, null)
                        .set(AiRecognitionTask::getStartedAt, null)
                        .set(AiRecognitionTask::getCompletedAt, null)
                        .set(AiRecognitionTask::getProcessingDurationMs, null)
                        .set(AiRecognitionTask::getRetryCount,
                                task.getRetryCount() + 1)
                        .setSql("version = version + 1")
                        .eq(AiRecognitionTask::getId, task.getId())
                        .eq(AiRecognitionTask::getStatus,
                                AiRecognitionStatus.FAILED.name())
                        .eq(AiRecognitionTask::getVersion,
                                retryDTO.version())
        );
        ensureRecognitionUpdated(updated);

        task.setStatus(AiRecognitionStatus.PENDING.name());
        task.setRetryCount(task.getRetryCount() + 1);
        task.setVersion(task.getVersion() + 1);
        saveOutboxEvent(task, requireImage(task.getImageId()));
        return toActionVO(task);
    }

    @Override
    @Transactional
    public AiRecognitionTaskActionVO cancelTask(
            Long adminId,
            Long recognitionTaskId,
            CancelAiRecognitionTaskDTO cancelDTO
    ) {
        requirePositiveId(adminId, "管理员ID不合法");
        AiRecognitionTask task = requireTaskForUpdate(recognitionTaskId);
        requireVersion(cancelDTO.version(), task.getVersion());
        requireTransition(statusOf(task), AiRecognitionStatus.CANCELLED);

        LocalDateTime cancelledAt = LocalDateTime.now();
        int updated = recognitionTaskMapper.update(
                null,
                Wrappers.<AiRecognitionTask>lambdaUpdate()
                        .set(AiRecognitionTask::getStatus,
                                AiRecognitionStatus.CANCELLED.name())
                        .set(AiRecognitionTask::getCancelReason,
                                cancelDTO.reason())
                        .set(AiRecognitionTask::getCancelledBy, adminId)
                        .set(AiRecognitionTask::getCancelledAt, cancelledAt)
                        .set(AiRecognitionTask::getCompletedAt, cancelledAt)
                        .setSql("version = version + 1")
                        .eq(AiRecognitionTask::getId, task.getId())
                        .eq(AiRecognitionTask::getStatus, task.getStatus())
                        .eq(AiRecognitionTask::getVersion,
                                cancelDTO.version())
        );
        ensureRecognitionUpdated(updated);

        // 尚未成功发送的消息不再发布；已经发送的消息由回调幂等逻辑拦截。
        outboxEventMapper.failUnsentByAggregate(
                AGGREGATE_TYPE,
                task.getId(),
                "业务任务已取消：" + cancelDTO.reason(),
                cancelledAt
        );
        failProcessingReadingTask(
                task.getReadingTaskId(),
                "AI识别任务已取消：" + cancelDTO.reason(),
                adminId
        );

        task.setStatus(AiRecognitionStatus.CANCELLED.name());
        task.setCancelReason(cancelDTO.reason());
        task.setCancelledBy(adminId);
        task.setCancelledAt(cancelledAt);
        task.setCompletedAt(cancelledAt);
        task.setVersion(task.getVersion() + 1);
        return toActionVO(task);
    }

    /** AI 服务开始处理；重复 start 回调不会重复推进状态。 */
    @Override
    @Transactional
    public void startTask(
            Long recognitionTaskId,
            StartAiRecognitionTaskDTO startDTO
    ) {
        AiRecognitionTask task = requireTaskForUpdate(recognitionTaskId);
        requireAttempt(task, startDTO.attemptNo());
        requireCurrentEvent(task.getId(), startDTO.eventId());

        AiRecognitionStatus currentStatus = statusOf(task);
        if (currentStatus == AiRecognitionStatus.PROCESSING) {
            return;
        }
        if (isTerminal(currentStatus)) {
            return;
        }
        requireTransition(currentStatus, AiRecognitionStatus.PROCESSING);

        int updated = recognitionTaskMapper.update(
                null,
                Wrappers.<AiRecognitionTask>lambdaUpdate()
                        .set(AiRecognitionTask::getStatus,
                                AiRecognitionStatus.PROCESSING.name())
                        .set(AiRecognitionTask::getModelName,
                                startDTO.modelName())
                        .set(AiRecognitionTask::getModelVersion,
                                startDTO.modelVersion())
                        .set(AiRecognitionTask::getStartedAt,
                                LocalDateTime.now())
                        .setSql("version = version + 1")
                        .eq(AiRecognitionTask::getId, task.getId())
                        .eq(AiRecognitionTask::getStatus,
                                AiRecognitionStatus.PENDING.name())
                        .eq(AiRecognitionTask::getVersion,
                                task.getVersion())
        );
        ensureRecognitionUpdated(updated);
    }

    /**
     * 识别成功后，在一个事务中生成待审核结果、绑定图片并推进抄表任务。
     */
    @Override
    @Transactional
    public void completeTask(
            Long recognitionTaskId,
            CompleteAiRecognitionTaskDTO completeDTO
    ) {
        AiRecognitionTask recognitionTask =
                requireTaskForUpdate(recognitionTaskId);
        requireAttempt(recognitionTask, completeDTO.attemptNo());
        requireCurrentEvent(recognitionTask.getId(), completeDTO.eventId());

        AiRecognitionStatus currentStatus = statusOf(recognitionTask);
        if (currentStatus == AiRecognitionStatus.SUCCEEDED
                && recognitionTask.getResultId() != null) {
            return;
        }
        if (currentStatus == AiRecognitionStatus.CANCELLED
                || currentStatus == AiRecognitionStatus.FAILED) {
            return;
        }
        requireTransition(currentStatus, AiRecognitionStatus.SUCCEEDED);
        validateJson(completeDTO.rawResult());

        MeterImage image = requireImage(recognitionTask.getImageId());
        requireUsableDeviceImage(image);
        MeterReadingTask readingTask = readingTaskMapper.selectByIdForUpdate(
                recognitionTask.getReadingTaskId()
        );
        requireProcessableReadingTask(readingTask, recognitionTask, image);

        LocalDateTime completedAt = LocalDateTime.now();
        Integer resultAttemptNo = readingResultMapper.selectNextAttemptNo(
                readingTask.getId()
        );
        MeterReadingResult result = MeterReadingResult.builder()
                .taskId(readingTask.getId())
                .attemptNo(resultAttemptNo)
                .meterId(readingTask.getMeterId())
                .sourceType(TaskExecutorType.DEVICE.name())
                .deviceId(readingTask.getDeviceId())
                .readingValue(completeDTO.recognizedValue())
                .recognitionConfidence(completeDTO.confidence())
                .remark("AI识别任务：" + recognitionTask.getRecognitionNo())
                .reviewStatus(MeterReadingReviewStatus.PENDING.name())
                .submittedAt(completedAt)
                .version(0)
                .build();
        try {
            readingResultMapper.insert(result);
            resultImageMapper.insert(MeterReadingResultImage.builder()
                    .resultId(result.getId())
                    .imageId(image.getId())
                    .sortOrder(0)
                    .createdAt(completedAt)
                    .build());
        } catch (DuplicateKeyException exception) {
            throw new ResourceConflictException(
                    "识别图片已生成抄表结果，请勿重复提交",
                    exception
            );
        }

        int recognitionUpdated = recognitionTaskMapper.update(
                null,
                Wrappers.<AiRecognitionTask>lambdaUpdate()
                        .set(AiRecognitionTask::getStatus,
                                AiRecognitionStatus.SUCCEEDED.name())
                        .set(AiRecognitionTask::getResultId, result.getId())
                        .set(AiRecognitionTask::getRecognizedValue,
                                completeDTO.recognizedValue())
                        .set(AiRecognitionTask::getConfidence,
                                completeDTO.confidence())
                        .set(AiRecognitionTask::getModelName,
                                completeDTO.modelName())
                        .set(AiRecognitionTask::getModelVersion,
                                completeDTO.modelVersion())
                        .set(AiRecognitionTask::getRawResult,
                                completeDTO.rawResult())
                        .set(AiRecognitionTask::getFailureCode, null)
                        .set(AiRecognitionTask::getFailureMessage, null)
                        .set(AiRecognitionTask::getProcessingDurationMs,
                                completeDTO.processingDurationMs())
                        .set(AiRecognitionTask::getCompletedAt, completedAt)
                        .setSql("version = version + 1")
                        .eq(AiRecognitionTask::getId,
                                recognitionTask.getId())
                        .eq(AiRecognitionTask::getStatus,
                                AiRecognitionStatus.PROCESSING.name())
                        .eq(AiRecognitionTask::getVersion,
                                recognitionTask.getVersion())
        );
        ensureRecognitionUpdated(recognitionUpdated);

        int taskUpdated = readingTaskMapper.update(
                null,
                Wrappers.<MeterReadingTask>lambdaUpdate()
                        .set(MeterReadingTask::getTaskStatus,
                                MeterReadingTaskStatus.PENDING_REVIEW.name())
                        .set(MeterReadingTask::getSubmittedAt, completedAt)
                        .set(MeterReadingTask::getFailedReason, null)
                        .setSql("version = version + 1")
                        .eq(MeterReadingTask::getId, readingTask.getId())
                        .eq(MeterReadingTask::getTaskStatus,
                                MeterReadingTaskStatus.PROCESSING.name())
                        .eq(MeterReadingTask::getVersion,
                                readingTask.getVersion())
        );
        if (taskUpdated != 1) {
            throw new VersionConflictException("抄表任务已被其他操作修改");
        }
    }

    /** AI 失败回调只接受当前最新事件，并同步结束对应抄表任务。 */
    @Override
    @Transactional
    public void failTask(
            Long recognitionTaskId,
            FailAiRecognitionTaskDTO failDTO
    ) {
        AiRecognitionTask recognitionTask =
                requireTaskForUpdate(recognitionTaskId);
        requireAttempt(recognitionTask, failDTO.attemptNo());
        requireCurrentEvent(recognitionTask.getId(), failDTO.eventId());

        AiRecognitionStatus currentStatus = statusOf(recognitionTask);
        if (currentStatus == AiRecognitionStatus.FAILED) {
            return;
        }
        if (currentStatus == AiRecognitionStatus.SUCCEEDED
                || currentStatus == AiRecognitionStatus.CANCELLED) {
            return;
        }
        requireTransition(currentStatus, AiRecognitionStatus.FAILED);

        LocalDateTime failedAt = LocalDateTime.now();
        int updated = recognitionTaskMapper.update(
                null,
                Wrappers.<AiRecognitionTask>lambdaUpdate()
                        .set(AiRecognitionTask::getStatus,
                                AiRecognitionStatus.FAILED.name())
                        .set(AiRecognitionTask::getFailureCode,
                                failDTO.failureCode())
                        .set(AiRecognitionTask::getFailureMessage,
                                failDTO.failureMessage())
                        .set(AiRecognitionTask::getProcessingDurationMs,
                                failDTO.processingDurationMs())
                        .set(AiRecognitionTask::getCompletedAt, failedAt)
                        .setSql("version = version + 1")
                        .eq(AiRecognitionTask::getId,
                                recognitionTask.getId())
                        .eq(AiRecognitionTask::getStatus,
                                AiRecognitionStatus.PROCESSING.name())
                        .eq(AiRecognitionTask::getVersion,
                                recognitionTask.getVersion())
        );
        ensureRecognitionUpdated(updated);
        failProcessingReadingTask(
                recognitionTask.getReadingTaskId(),
                "AI识别失败：" + failDTO.failureMessage(),
                null
        );
    }

    private @NonNull AiRecognitionTaskActionVO createTask(Long imageId) {
        MeterImage image = requireImage(imageId);
        requireUsableDeviceImage(image);
        MeterReadingTask readingTask = readingTaskMapper.selectByIdForUpdate(
                image.getTaskId()
        );
        requireProcessableReadingTask(readingTask, null, image);

        long activeCount = recognitionTaskMapper.selectCount(
                Wrappers.<AiRecognitionTask>lambdaQuery()
                        .eq(AiRecognitionTask::getImageId, imageId)
                        .in(AiRecognitionTask::getStatus,
                                AiRecognitionStatus.PENDING.name(),
                                AiRecognitionStatus.PROCESSING.name())
        );
        if (activeCount > 0) {
            throw new ResourceConflictException(
                    "该图片已经存在待处理的AI识别任务"
            );
        }

        Integer attemptNo = recognitionTaskMapper.selectNextAttemptNo(imageId);
        AiRecognitionTask task = AiRecognitionTask.builder()
                .recognitionNo(generateRecognitionNo())
                .readingTaskId(readingTask.getId())
                .imageId(image.getId())
                .meterId(readingTask.getMeterId())
                .attemptNo(attemptNo)
                .status(AiRecognitionStatus.PENDING.name())
                .retryCount(0)
                .maxRetryCount(3)
                .version(0)
                .build();
        try {
            recognitionTaskMapper.insert(task);
            saveOutboxEvent(task, image);
        } catch (DuplicateKeyException exception) {
            throw new ResourceConflictException(
                    "该图片的识别任务已被并发创建，请刷新后重试",
                    exception
            );
        }
        return toActionVO(task);
    }

    private void saveOutboxEvent(
            AiRecognitionTask task,
            MeterImage image
    ) {
        String eventId = generateEventId();
        String payload = writeJson(Map.of(
                "eventId", eventId,
                "recognitionTaskId", task.getId(),
                "readingTaskId", task.getReadingTaskId(),
                "imageId", task.getImageId(),
                "meterId", task.getMeterId(),
                "bucketName", image.getBucketName(),
                "objectKey", image.getObjectKey(),
                "attemptNo", task.getAttemptNo()
        ));
        MqOutboxEvent event = MqOutboxEvent.builder()
                .eventId(eventId)
                .aggregateType(AGGREGATE_TYPE)
                .aggregateId(task.getId())
                .eventType(EVENT_TYPE)
                .exchangeName(EXCHANGE_NAME)
                .routingKey(ROUTING_KEY)
                .payload(payload)
                .status(MqOutboxStatus.PENDING.name())
                .retryCount(0)
                .maxRetryCount(5)
                .nextRetryAt(LocalDateTime.now())
                .version(0)
                .build();
        outboxEventMapper.insert(event);
    }

    private void restoreReadingTaskForRetry(
            MeterReadingTask readingTask,
            Long adminId
    ) {
        MeterReadingTaskStatus status = MeterReadingTaskStatus.valueOf(
                readingTask.getTaskStatus()
        );
        if (status == MeterReadingTaskStatus.PROCESSING) {
            return;
        }
        if (status != MeterReadingTaskStatus.FAILED) {
            throw new IllegalArgumentException(
                    "关联抄表任务当前不允许重新执行AI识别"
            );
        }

        int reset = readingTaskMapper.update(
                null,
                Wrappers.<MeterReadingTask>lambdaUpdate()
                        .set(MeterReadingTask::getTaskStatus,
                                MeterReadingTaskStatus.PENDING.name())
                        .set(MeterReadingTask::getFailedReason, null)
                        .set(MeterReadingTask::getUpdatedBy, adminId)
                        .setSql("retry_count = retry_count + 1")
                        .setSql("version = version + 1")
                        .eq(MeterReadingTask::getId, readingTask.getId())
                        .eq(MeterReadingTask::getTaskStatus,
                                MeterReadingTaskStatus.FAILED.name())
                        .eq(MeterReadingTask::getVersion,
                                readingTask.getVersion())
        );
        if (reset != 1) {
            throw new VersionConflictException("抄表任务已被其他操作修改");
        }

        int restarted = readingTaskMapper.update(
                null,
                Wrappers.<MeterReadingTask>lambdaUpdate()
                        .set(MeterReadingTask::getTaskStatus,
                                MeterReadingTaskStatus.PROCESSING.name())
                        .set(MeterReadingTask::getStartedAt,
                                LocalDateTime.now())
                        .setSql("version = version + 1")
                        .eq(MeterReadingTask::getId, readingTask.getId())
                        .eq(MeterReadingTask::getTaskStatus,
                                MeterReadingTaskStatus.PENDING.name())
                        .eq(MeterReadingTask::getVersion,
                                readingTask.getVersion() + 1)
        );
        if (restarted != 1) {
            throw new VersionConflictException("抄表任务重新执行失败");
        }
    }

    private void failProcessingReadingTask(
            Long readingTaskId,
            String reason,
            Long operatorId
    ) {
        var update = Wrappers.<MeterReadingTask>lambdaUpdate()
                .set(MeterReadingTask::getTaskStatus,
                        MeterReadingTaskStatus.FAILED.name())
                .set(MeterReadingTask::getFailedReason, reason)
                .set(operatorId != null,
                        MeterReadingTask::getUpdatedBy, operatorId)
                .setSql("version = version + 1")
                .eq(MeterReadingTask::getId, readingTaskId)
                .eq(MeterReadingTask::getTaskStatus,
                        MeterReadingTaskStatus.PROCESSING.name());
        readingTaskMapper.update(null, update);
    }

    private void requireCurrentEvent(Long taskId, String eventId) {
        MqOutboxEvent event = outboxEventMapper.selectLatestByAggregate(
                AGGREGATE_TYPE, taskId
        );
        if (event == null || !eventId.equals(event.getEventId())) {
            throw new ResourceConflictException(
                    "AI回调事件不是该识别任务当前有效的消息"
            );
        }
        MqOutboxStatus status = MqOutboxStatus.valueOf(event.getStatus());
        if (status != MqOutboxStatus.SENDING
                && status != MqOutboxStatus.SENT) {
            throw new ResourceConflictException("AI回调对应的消息尚未成功发布");
        }
    }

    private MeterImage requireImage(Long imageId) {
        requirePositiveId(imageId, "图片ID必须大于0");
        MeterImage image = imageMapper.selectById(imageId);
        if (image == null) {
            throw new ResourceNotFoundException("抄表图片不存在");
        }
        return image;
    }

    private void requireUsableDeviceImage(MeterImage image) {
        boolean usable = TaskExecutorType.DEVICE.name()
                .equals(image.getUploaderType())
                && Integer.valueOf(0).equals(image.getDeleted())
                && MeterImageStatus.VALID.name()
                .equals(image.getImageStatus())
                && MeterImageStorageStatus.STORED.name()
                .equals(image.getStorageStatus());
        if (!usable) {
            throw new IllegalArgumentException(
                    "AI识别只接受有效、未删除且已存入OSS的设备图片"
            );
        }
    }

    private void requireProcessableReadingTask(
            MeterReadingTask readingTask,
            AiRecognitionTask recognitionTask,
            MeterImage image
    ) {
        if (readingTask == null) {
            throw new ResourceNotFoundException("关联抄表任务不存在");
        }
        boolean valid = image.getTaskId().equals(readingTask.getId())
                && image.getUploaderId().equals(readingTask.getDeviceId())
                && (recognitionTask == null
                || readingTask.getMeterId().equals(
                        recognitionTask.getMeterId()
                ))
                && TaskExecutorType.DEVICE.name()
                .equals(readingTask.getExecutorType())
                && MeterReadingTaskStatus.PROCESSING.name()
                .equals(readingTask.getTaskStatus());
        if (!valid) {
            throw new IllegalArgumentException(
                    "图片、设备、表具与执行中的抄表任务不匹配"
            );
        }
    }

    private AiRecognitionTask requireTaskForUpdate(Long taskId) {
        requirePositiveId(taskId, "识别任务ID必须大于0");
        AiRecognitionTask task = recognitionTaskMapper.selectByIdForUpdate(
                taskId
        );
        if (task == null) {
            throw new ResourceNotFoundException("AI识别任务不存在");
        }
        return task;
    }

    private void requireAttempt(AiRecognitionTask task, int attemptNo) {
        if (!Integer.valueOf(attemptNo).equals(task.getAttemptNo())) {
            throw new ResourceConflictException("AI回调识别尝试序号不匹配");
        }
    }

    private void requireVersion(Integer expected, Integer actual) {
        if (expected == null || expected < 0) {
            throw new IllegalArgumentException("识别任务版本不能为空且不能小于0");
        }
        if (!expected.equals(actual)) {
            throw new VersionConflictException(
                    "AI识别任务已被其他操作修改，请刷新后重试"
            );
        }
    }

    private void requireTransition(
            AiRecognitionStatus current,
            AiRecognitionStatus target
    ) {
        if (!current.canTransitionTo(target)) {
            throw new IllegalArgumentException(
                    "AI识别任务不能从" + current.getDescription()
                            + "变为" + target.getDescription()
            );
        }
    }

    private void ensureRecognitionUpdated(int updated) {
        if (updated != 1) {
            throw new VersionConflictException(
                    "AI识别任务已被其他操作修改，请刷新后重试"
            );
        }
    }

    private AiRecognitionStatus statusOf(AiRecognitionTask task) {
        return AiRecognitionStatus.valueOf(task.getStatus());
    }

    private boolean isTerminal(AiRecognitionStatus status) {
        return status == AiRecognitionStatus.SUCCEEDED
                || status == AiRecognitionStatus.FAILED
                || status == AiRecognitionStatus.CANCELLED;
    }

    private void validateCreatedRange(
            AiRecognitionTaskPageQueryDTO queryDTO
    ) {
        if (queryDTO.createdAtStart() != null
                && queryDTO.createdAtEnd() != null
                && queryDTO.createdAtEnd().isBefore(
                queryDTO.createdAtStart()
        )) {
            throw new IllegalArgumentException("创建结束时间不能早于开始时间");
        }
    }

    private void validateJson(String rawResult) {
        try {
            objectMapper.readTree(rawResult);
        } catch (Exception exception) {
            throw new IllegalArgumentException("原始识别结果不是合法JSON");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("识别任务消息序列化失败", exception);
        }
    }

    private String generateRecognitionNo() {
        return RECOGNITION_NO_PREFIX
                + UUID.randomUUID().toString().replace("-", "");
    }

    private String generateEventId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private void requirePositiveId(Long id, String message) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private AiRecognitionTaskActionVO toActionVO(
            AiRecognitionTask task
    ) {
        AiRecognitionStatus status = statusOf(task);
        return new AiRecognitionTaskActionVO(
                task.getId(), task.getRecognitionNo(), status,
                status.getDescription(), task.getVersion()
        );
    }

    private AiRecognitionTaskListVO toListVO(
            AiRecognitionTaskListRow row
    ) {
        AiRecognitionStatus status = AiRecognitionStatus.valueOf(
                row.getStatus()
        );
        return new AiRecognitionTaskListVO(
                row.getRecognitionTaskId(), row.getRecognitionNo(),
                row.getReadingTaskId(), row.getReadingTaskNo(),
                row.getImageId(), row.getMeterId(), row.getMeterNo(),
                row.getMeterName(), row.getAttemptNo(), status,
                status.getDescription(), row.getRecognizedValue(),
                row.getConfidence(), row.getModelName(),
                row.getModelVersion(), row.getRetryCount(),
                row.getMaxRetryCount(), row.getVersion(),
                row.getStartedAt(), row.getCompletedAt(), row.getCreatedAt()
        );
    }

    private AiRecognitionTaskDetailVO toDetailVO(
            AiRecognitionTaskDetailRow row
    ) {
        MeterReadingTaskStatus readingTaskStatus =
                MeterReadingTaskStatus.valueOf(row.getReadingTaskStatus());
        MeterImageType imageType = MeterImageType.valueOf(row.getImageType());
        MeterImageStatus imageStatus = MeterImageStatus.valueOf(
                row.getImageStatus()
        );
        MeterImageStorageStatus storageStatus =
                MeterImageStorageStatus.valueOf(row.getImageStorageStatus());
        AiRecognitionStatus recognitionStatus =
                AiRecognitionStatus.valueOf(row.getStatus());
        MqOutboxStatus outboxStatus = row.getLatestOutboxStatus() == null
                ? null
                : MqOutboxStatus.valueOf(row.getLatestOutboxStatus());

        return new AiRecognitionTaskDetailVO(
                row.getRecognitionTaskId(), row.getRecognitionNo(),
                row.getReadingTaskId(), row.getReadingTaskNo(),
                readingTaskStatus, readingTaskStatus.getDescription(),
                row.getImageId(), row.getImageOriginalName(), imageType,
                imageType.getDescription(), imageStatus,
                imageStatus.getDescription(), storageStatus,
                storageStatus.getDescription(), row.getMeterId(),
                row.getMeterNo(), row.getMeterName(), row.getMeterType(),
                row.getUnit(), row.getAttemptNo(), recognitionStatus,
                recognitionStatus.getDescription(), row.getResultId(),
                row.getRecognizedValue(), row.getConfidence(),
                row.getModelName(), row.getModelVersion(),
                row.getRawResult(), row.getFailureCode(),
                row.getFailureMessage(), row.getRetryCount(),
                row.getMaxRetryCount(), row.getStartedAt(),
                row.getCompletedAt(), row.getProcessingDurationMs(),
                row.getCancelReason(), row.getCancelledBy(),
                row.getCancelledByName(), row.getCancelledAt(),
                row.getVersion(), row.getCreatedAt(), row.getUpdatedAt(),
                row.getLatestEventId(), outboxStatus,
                outboxStatus == null ? null : outboxStatus.getDescription(),
                row.getLatestOutboxRetryCount(),
                row.getLatestOutboxMaxRetryCount(),
                row.getLatestOutboxNextRetryAt(),
                row.getLatestOutboxSentAt(),
                row.getLatestOutboxLastError()
        );
    }
}
