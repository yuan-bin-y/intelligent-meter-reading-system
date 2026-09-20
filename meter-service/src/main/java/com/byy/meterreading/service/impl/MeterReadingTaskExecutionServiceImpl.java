package com.byy.meterreading.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byy.meterreading.common.exception.ResourceConflictException;
import com.byy.meterreading.common.exception.ResourceNotFoundException;
import com.byy.meterreading.common.exception.VersionConflictException;
import com.byy.meterreading.dto.meterreadingtask.DevicePendingTaskPageQueryDTO;
import com.byy.meterreading.dto.meterreadingtask.MyMeterReadingTaskPageQueryDTO;
import com.byy.meterreading.dto.meterreadingtask.SubmitDeviceReadingResultDTO;
import com.byy.meterreading.dto.meterreadingtask.SubmitManualReadingResultDTO;
import com.byy.meterreading.mapper.MeterMapper;
import com.byy.meterreading.mapper.MeterReadingResultMapper;
import com.byy.meterreading.mapper.MeterReadingTaskMapper;
import com.byy.meterreading.mapper.projection.MeterReadingTaskDetailRow;
import com.byy.meterreading.mapper.projection.MeterReadingTaskListRow;
import com.byy.meterreading.model.Meter;
import com.byy.meterreading.model.MeterReadingResult;
import com.byy.meterreading.model.MeterReadingTask;
import com.byy.meterreading.model.enums.MeterDisplayType;
import com.byy.meterreading.model.enums.MeterReadingReviewStatus;
import com.byy.meterreading.model.enums.MeterReadingTaskStatus;
import com.byy.meterreading.model.enums.MeterType;
import com.byy.meterreading.model.enums.TaskExecutorType;
import com.byy.meterreading.service.MeterReadingTaskExecutionService;
import com.byy.meterreading.vo.common.PageVO;
import com.byy.meterreading.vo.meterreadingtask.MeterReadingSubmissionVO;
import com.byy.meterreading.vo.meterreadingtask.MeterReadingTaskDetailVO;
import com.byy.meterreading.vo.meterreadingtask.MeterReadingTaskListItemVO;
import com.byy.meterreading.vo.meterreadingtask.MeterReadingTaskVersionVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 人工抄表和设备自动抄表的任务执行实现。
 */
@Service
public class MeterReadingTaskExecutionServiceImpl
        implements MeterReadingTaskExecutionService {

    private final MeterReadingTaskMapper meterReadingTaskMapper;
    private final MeterReadingResultMapper meterReadingResultMapper;
    private final MeterMapper meterMapper;

    public MeterReadingTaskExecutionServiceImpl(
            MeterReadingTaskMapper meterReadingTaskMapper,
            MeterReadingResultMapper meterReadingResultMapper,
            MeterMapper meterMapper
    ) {
        this.meterReadingTaskMapper = meterReadingTaskMapper;
        this.meterReadingResultMapper = meterReadingResultMapper;
        this.meterMapper = meterMapper;
    }

    /**
     * 查询当前 JWT 用户被分配的人工任务；归属条件直接进入 SQL。
     */
    @Override
    @Transactional(readOnly = true)
    public PageVO<MeterReadingTaskListItemVO> listReaderTasks(
            Long meterReaderId,
            MyMeterReadingTaskPageQueryDTO queryDTO
    ) {
        requirePositiveExecutorId(meterReaderId, "抄表员ID不合法");
        return listOwnedTasks(
                TaskExecutorType.METER_READER,
                meterReaderId,
                queryDTO.page(),
                queryDTO.pageSize(),
                queryDTO.taskStatus()
        );
    }

    /**
     * 查询详情前校验任务归属，不能通过任务 ID 查看其他抄表员的任务。
     */
    @Override
    @Transactional(readOnly = true)
    public MeterReadingTaskDetailVO getReaderTask(
            Long meterReaderId,
            Long taskId
    ) {
        requireOwnedTask(
                taskId,
                TaskExecutorType.METER_READER,
                meterReaderId
        );
        return toDetailVO(requireTaskDetail(taskId));
    }

    @Override
    @Transactional
    public MeterReadingTaskVersionVO startReaderTask(
            Long meterReaderId,
            Long taskId,
            Integer version
    ) {
        return startTask(
                TaskExecutorType.METER_READER,
                meterReaderId,
                taskId,
                version
        );
    }

    /**
     * 在同一个 MySQL 事务中保存人工读数并推进任务状态。
     */
    @Override
    @Transactional
    public MeterReadingSubmissionVO submitManualResult(
            Long meterReaderId,
            Long taskId,
            SubmitManualReadingResultDTO resultDTO
    ) {
        return submitResult(
                TaskExecutorType.METER_READER,
                meterReaderId,
                taskId,
                resultDTO.version(),
                resultDTO.readingValue(),
                resultDTO.imageUrl(),
                null,
                resultDTO.remark()
        );
    }

    /**
     * 设备拉取接口固定只返回 PENDING 任务，避免重复接收执行中任务。
     */
    @Override
    @Transactional(readOnly = true)
    public PageVO<MeterReadingTaskListItemVO> listDevicePendingTasks(
            Long deviceId,
            DevicePendingTaskPageQueryDTO queryDTO
    ) {
        requirePositiveExecutorId(deviceId, "设备ID不合法");
        return listOwnedTasks(
                TaskExecutorType.DEVICE,
                deviceId,
                queryDTO.page(),
                queryDTO.pageSize(),
                MeterReadingTaskStatus.PENDING
        );
    }

    @Override
    @Transactional
    public MeterReadingTaskVersionVO acceptDeviceTask(
            Long deviceId,
            Long taskId,
            Integer version
    ) {
        return startTask(
                TaskExecutorType.DEVICE,
                deviceId,
                taskId,
                version
        );
    }

    /**
     * 在同一个 MySQL 事务中保存设备识别结果并推进任务状态。
     */
    @Override
    @Transactional
    public MeterReadingSubmissionVO submitDeviceResult(
            Long deviceId,
            Long taskId,
            SubmitDeviceReadingResultDTO resultDTO
    ) {
        return submitResult(
                TaskExecutorType.DEVICE,
                deviceId,
                taskId,
                resultDTO.version(),
                resultDTO.recognizedReading(),
                resultDTO.imageUrl(),
                resultDTO.confidence(),
                resultDTO.remark()
        );
    }

    /**
     * 复用管理员任务分页 SQL，并强制附加当前执行者 ID。
     */
    private PageVO<MeterReadingTaskListItemVO> listOwnedTasks(
            TaskExecutorType executorType,
            Long executorId,
            int pageNumber,
            int pageSize,
            MeterReadingTaskStatus taskStatus
    ) {
        Page<MeterReadingTaskListRow> page = new Page<>(
                pageNumber,
                pageSize
        );
        IPage<MeterReadingTaskListRow> result =
                meterReadingTaskMapper.selectTaskPage(
                        page,
                        null,
                        executorType.name(),
                        taskStatus == null ? null : taskStatus.name(),
                        executorType == TaskExecutorType.METER_READER
                                ? executorId
                                : null,
                        executorType == TaskExecutorType.DEVICE
                                ? executorId
                                : null,
                        null,
                        null
                );
        return new PageVO<>(
                result.getRecords().stream()
                        .map(this::toListItemVO)
                        .toList(),
                result.getTotal(),
                result.getCurrent(),
                result.getSize()
        );
    }

    /**
     * 人工“开始”和设备“接收”本质上都是 PENDING → PROCESSING。
     */
    private MeterReadingTaskVersionVO startTask(
            TaskExecutorType executorType,
            Long executorId,
            Long taskId,
            Integer expectedVersion
    ) {
        MeterReadingTask task = requireOwnedTask(
                taskId,
                executorType,
                executorId
        );
        requireExpectedVersion(task, expectedVersion);
        requireTransition(
                taskStatusOf(task),
                MeterReadingTaskStatus.PROCESSING
        );

        LocalDateTime startedAt = LocalDateTime.now();
        LambdaUpdateWrapper<MeterReadingTask> updateWrapper =
                ownedTaskUpdate(executorType, executorId, taskId)
                        .set(MeterReadingTask::getTaskStatus,
                                MeterReadingTaskStatus.PROCESSING.name())
                        .set(MeterReadingTask::getStartedAt, startedAt)
                        .set(MeterReadingTask::getFailedReason, null)
                        .setSql("version = version + 1")
                        .eq(MeterReadingTask::getTaskStatus,
                                MeterReadingTaskStatus.PENDING.name())
                        .eq(MeterReadingTask::getVersion,
                                expectedVersion);

        int updatedRows = meterReadingTaskMapper.update(
                null,
                updateWrapper
        );
        ensureUpdated(updatedRows);
        return new MeterReadingTaskVersionVO(
                taskId,
                MeterReadingTaskStatus.PROCESSING,
                expectedVersion + 1
        );
    }

    /**
     * 先保存结果，再更新任务；后一步失败时 @Transactional 会回滚前一步。
     */
    private MeterReadingSubmissionVO submitResult(
            TaskExecutorType executorType,
            Long executorId,
            Long taskId,
            Integer expectedVersion,
            BigDecimal readingValue,
            String imageUrl,
            BigDecimal confidence,
            String remark
    ) {
        MeterReadingTask task = requireOwnedTask(
                taskId,
                executorType,
                executorId
        );
        requireExpectedVersion(task, expectedVersion);
        requireTransition(
                taskStatusOf(task),
                MeterReadingTaskStatus.PENDING_REVIEW
        );
        validateReading(task.getMeterId(), readingValue);

        LocalDateTime submittedAt = LocalDateTime.now();
        MeterReadingResult result = MeterReadingResult.builder()
                .taskId(taskId)
                .meterId(task.getMeterId())
                .sourceType(executorType.name())
                .meterReaderId(executorType == TaskExecutorType.METER_READER
                        ? executorId
                        : null)
                .deviceId(executorType == TaskExecutorType.DEVICE
                        ? executorId
                        : null)
                .readingValue(readingValue)
                .imageUrl(imageUrl)
                .recognitionConfidence(confidence)
                .remark(remark)
                .reviewStatus(MeterReadingReviewStatus.PENDING.name())
                .submittedAt(submittedAt)
                .version(0)
                .build();
        try {
            meterReadingResultMapper.insert(result);
        } catch (DuplicateKeyException exception) {
            throw new ResourceConflictException(
                    "该任务已经提交过抄表结果",
                    exception
            );
        }

        LambdaUpdateWrapper<MeterReadingTask> updateWrapper =
                ownedTaskUpdate(executorType, executorId, taskId)
                        .set(MeterReadingTask::getTaskStatus,
                                MeterReadingTaskStatus.PENDING_REVIEW.name())
                        .set(MeterReadingTask::getSubmittedAt, submittedAt)
                        .setSql("version = version + 1")
                        .eq(MeterReadingTask::getTaskStatus,
                                MeterReadingTaskStatus.PROCESSING.name())
                        .eq(MeterReadingTask::getVersion,
                                expectedVersion);
        int updatedRows = meterReadingTaskMapper.update(
                null,
                updateWrapper
        );
        ensureUpdated(updatedRows);

        return new MeterReadingSubmissionVO(
                result.getId(),
                taskId,
                readingValue,
                MeterReadingReviewStatus.PENDING,
                MeterReadingTaskStatus.PENDING_REVIEW,
                expectedVersion + 1,
                submittedAt
        );
    }

    /**
     * 将执行者类型和身份放进 UPDATE 条件，防止操作其他执行者的任务。
     */
    private LambdaUpdateWrapper<MeterReadingTask> ownedTaskUpdate(
            TaskExecutorType executorType,
            Long executorId,
            Long taskId
    ) {
        return Wrappers.<MeterReadingTask>lambdaUpdate()
                .eq(MeterReadingTask::getId, taskId)
                .eq(MeterReadingTask::getExecutorType,
                        executorType.name())
                .eq(executorType == TaskExecutorType.METER_READER,
                        MeterReadingTask::getMeterReaderId,
                        executorId)
                .eq(executorType == TaskExecutorType.DEVICE,
                        MeterReadingTask::getDeviceId,
                        executorId);
    }

    private MeterReadingTask requireOwnedTask(
            Long taskId,
            TaskExecutorType executorType,
            Long executorId
    ) {
        requirePositiveExecutorId(executorId, "当前执行者身份不合法");
        if (taskId == null || taskId <= 0) {
            throw new IllegalArgumentException("任务ID必须大于0");
        }
        MeterReadingTask task = meterReadingTaskMapper.selectById(taskId);
        if (task == null
                || !executorType.name().equals(task.getExecutorType())
                || !executorId.equals(executorIdOf(task, executorType))) {
            throw new ResourceNotFoundException(
                    "抄表任务不存在或不属于当前执行者"
            );
        }
        return task;
    }

    private Long executorIdOf(
            MeterReadingTask task,
            TaskExecutorType executorType
    ) {
        return executorType == TaskExecutorType.METER_READER
                ? task.getMeterReaderId()
                : task.getDeviceId();
    }

    private MeterReadingTaskDetailRow requireTaskDetail(Long taskId) {
        MeterReadingTaskDetailRow row =
                meterReadingTaskMapper.selectTaskDetail(taskId);
        if (row == null) {
            throw new ResourceNotFoundException("抄表任务不存在");
        }
        return row;
    }

    private void requireTransition(
            MeterReadingTaskStatus currentStatus,
            MeterReadingTaskStatus targetStatus
    ) {
        if (!currentStatus.canTransitionTo(targetStatus)) {
            throw new IllegalArgumentException(
                    "任务不能从" + currentStatus.getDescription()
                            + "变为" + targetStatus.getDescription()
            );
        }
    }

    private MeterReadingTaskStatus taskStatusOf(
            MeterReadingTask task
    ) {
        return MeterReadingTaskStatus.valueOf(task.getTaskStatus());
    }

    private void requireExpectedVersion(
            MeterReadingTask task,
            Integer expectedVersion
    ) {
        if (expectedVersion == null || expectedVersion < 0) {
            throw new IllegalArgumentException(
                    "数据版本不能为空且不能小于0"
            );
        }
        if (!expectedVersion.equals(task.getVersion())) {
            throw new VersionConflictException(
                    "抄表任务已被其他操作修改，请刷新后重试"
            );
        }
    }

    /**
     * DTO 先做通用精度校验，这里再按具体表具的位数配置检查读数。
     */
    private void validateReading(Long meterId, BigDecimal readingValue) {
        Meter meter = meterMapper.selectById(meterId);
        if (meter == null) {
            throw new ResourceNotFoundException("任务关联的表具不存在");
        }
        BigDecimal normalized = readingValue.stripTrailingZeros();
        int decimalDigits = Math.max(normalized.scale(), 0);
        int integerDigits = normalized.precision() - normalized.scale();
        if (decimalDigits > meter.getDecimalDigits()
                || integerDigits > meter.getIntegerDigits()) {
            throw new IllegalArgumentException(
                    "提交读数不符合表具的整数位和小数位配置"
            );
        }
    }

    private void ensureUpdated(int updatedRows) {
        if (updatedRows != 1) {
            throw new VersionConflictException(
                    "抄表任务已被其他操作修改，请刷新后重试"
            );
        }
    }

    private void requirePositiveExecutorId(Long id, String message) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private MeterReadingTaskListItemVO toListItemVO(
            MeterReadingTaskListRow row
    ) {
        TaskExecutorType executorType = TaskExecutorType.valueOf(
                row.getExecutorType()
        );
        MeterReadingTaskStatus taskStatus =
                MeterReadingTaskStatus.valueOf(row.getTaskStatus());
        return new MeterReadingTaskListItemVO(
                row.getTaskId(),
                row.getTaskNo(),
                row.getMeterId(),
                row.getMeterNo(),
                row.getMeterName(),
                executorType,
                executorType.getDescription(),
                row.getExecutorId(),
                row.getExecutorName(),
                taskStatus,
                taskStatus.getDescription(),
                row.getScheduledAt(),
                row.getRetryCount(),
                row.getVersion(),
                row.getUpdatedAt()
        );
    }

    private MeterReadingTaskDetailVO toDetailVO(
            MeterReadingTaskDetailRow row
    ) {
        TaskExecutorType executorType = TaskExecutorType.valueOf(
                row.getExecutorType()
        );
        MeterReadingTaskStatus taskStatus =
                MeterReadingTaskStatus.valueOf(row.getTaskStatus());
        return new MeterReadingTaskDetailVO(
                row.getTaskId(),
                row.getTaskNo(),
                row.getMeterId(),
                row.getMeterNo(),
                row.getMeterName(),
                MeterType.valueOf(row.getMeterType()),
                MeterDisplayType.valueOf(row.getDisplayType()),
                row.getUnit(),
                executorType,
                executorType.getDescription(),
                row.getExecutorId(),
                row.getExecutorCode(),
                row.getExecutorName(),
                taskStatus,
                taskStatus.getDescription(),
                row.getScheduledAt(),
                row.getStartedAt(),
                row.getSubmittedAt(),
                row.getCompletedAt(),
                row.getCancelledAt(),
                row.getFailedReason(),
                row.getCancelReason(),
                row.getRetryCount(),
                row.getVersion(),
                row.getRemark(),
                row.getCreatedBy(),
                row.getUpdatedBy(),
                row.getCreatedAt(),
                row.getUpdatedAt()
        );
    }
}
