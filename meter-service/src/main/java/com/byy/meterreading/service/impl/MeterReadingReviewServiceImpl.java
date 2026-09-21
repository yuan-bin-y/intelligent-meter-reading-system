package com.byy.meterreading.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byy.meterreading.common.exception.ResourceConflictException;
import com.byy.meterreading.common.exception.ResourceNotFoundException;
import com.byy.meterreading.common.exception.VersionConflictException;
import com.byy.meterreading.dto.meterreadingreview.ApproveMeterReadingResultDTO;
import com.byy.meterreading.dto.meterreadingreview.MeterReadingResultPageQueryDTO;
import com.byy.meterreading.dto.meterreadingreview.RejectMeterReadingResultDTO;
import com.byy.meterreading.mapper.MeterImageMapper;
import com.byy.meterreading.mapper.MeterReadingRecordMapper;
import com.byy.meterreading.mapper.MeterReadingResultMapper;
import com.byy.meterreading.mapper.MeterReadingReviewMapper;
import com.byy.meterreading.mapper.MeterReadingTaskMapper;
import com.byy.meterreading.mapper.projection.MeterImageRow;
import com.byy.meterreading.mapper.projection.MeterReadingResultRow;
import com.byy.meterreading.mapper.projection.MeterReadingReviewHistoryRow;
import com.byy.meterreading.model.MeterReadingRecord;
import com.byy.meterreading.model.MeterReadingResult;
import com.byy.meterreading.model.MeterReadingReview;
import com.byy.meterreading.model.MeterReadingTask;
import com.byy.meterreading.model.enums.MeterImageStatus;
import com.byy.meterreading.model.enums.MeterImageStorageStatus;
import com.byy.meterreading.model.enums.MeterImageType;
import com.byy.meterreading.model.enums.MeterReadingReviewStatus;
import com.byy.meterreading.model.enums.MeterReadingTaskStatus;
import com.byy.meterreading.model.enums.TaskExecutorType;
import com.byy.meterreading.service.MeterReadingReviewService;
import com.byy.meterreading.service.BusinessNotificationService;
import com.byy.meterreading.vo.common.PageVO;
import com.byy.meterreading.vo.meterimage.MeterImageItemVO;
import com.byy.meterreading.vo.meterreadingreview.MeterReadingResultDetailVO;
import com.byy.meterreading.vo.meterreadingreview.MeterReadingResultListItemVO;
import com.byy.meterreading.vo.meterreadingreview.MeterReadingReviewDecisionVO;
import com.byy.meterreading.vo.meterreadingreview.MeterReadingReviewHistoryVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 审核状态机及跨表事务实现。 */
@Service
public class MeterReadingReviewServiceImpl
        implements MeterReadingReviewService {

    private final MeterReadingResultMapper resultMapper;
    private final MeterReadingTaskMapper taskMapper;
    private final MeterReadingReviewMapper reviewMapper;
    private final MeterReadingRecordMapper recordMapper;
    private final MeterImageMapper imageMapper;
    private final BusinessNotificationService businessNotificationService;

    public MeterReadingReviewServiceImpl(
            MeterReadingResultMapper resultMapper,
            MeterReadingTaskMapper taskMapper,
            MeterReadingReviewMapper reviewMapper,
            MeterReadingRecordMapper recordMapper,
            MeterImageMapper imageMapper,
            BusinessNotificationService businessNotificationService
    ) {
        this.resultMapper = resultMapper;
        this.taskMapper = taskMapper;
        this.reviewMapper = reviewMapper;
        this.recordMapper = recordMapper;
        this.imageMapper = imageMapper;
        this.businessNotificationService = businessNotificationService;
    }

    @Override
    @Transactional(readOnly = true)
    public PageVO<MeterReadingResultListItemVO> listResults(
            MeterReadingResultPageQueryDTO queryDTO
    ) {
        validateSubmittedRange(queryDTO);
        Page<MeterReadingResultRow> page = new Page<>(
                queryDTO.page(), queryDTO.pageSize()
        );
        IPage<MeterReadingResultRow> result = resultMapper.selectResultPage(
                page,
                queryDTO.keyword(),
                enumName(queryDTO.sourceType()),
                enumName(queryDTO.reviewStatus()),
                queryDTO.submittedAtStart(),
                queryDTO.submittedAtEnd()
        );
        return new PageVO<>(
                result.getRecords().stream().map(this::toListVO).toList(),
                result.getTotal(), result.getCurrent(), result.getSize()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public MeterReadingResultDetailVO getResult(Long resultId) {
        return buildDetail(requireResultRow(resultId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MeterReadingReviewHistoryVO> listReviewHistory(Long resultId) {
        requireResultRow(resultId);
        return reviewMapper.selectResultReviewHistory(resultId)
                .stream()
                .map(this::toHistoryVO)
                .toList();
    }

    @Override
    @Transactional
    public MeterReadingReviewDecisionVO approve(
            Long reviewerId,
            Long resultId,
            ApproveMeterReadingResultDTO approveDTO
    ) {
        return review(
                reviewerId,
                resultId,
                approveDTO.resultVersion(),
                approveDTO.taskVersion(),
                MeterReadingReviewStatus.APPROVED,
                approveDTO.confirmedReadingValue(),
                approveDTO.remark()
        );
    }

    @Override
    @Transactional
    public MeterReadingReviewDecisionVO reject(
            Long reviewerId,
            Long resultId,
            RejectMeterReadingResultDTO rejectDTO
    ) {
        return review(
                reviewerId,
                resultId,
                rejectDTO.resultVersion(),
                rejectDTO.taskVersion(),
                MeterReadingReviewStatus.REJECTED,
                null,
                rejectDTO.reason()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public MeterReadingResultDetailVO getLatestReaderResult(
            Long meterReaderId,
            Long taskId
    ) {
        requirePositiveId(meterReaderId, "抄表员ID不合法");
        requirePositiveId(taskId, "任务ID必须大于0");
        Long resultId = resultMapper.selectLatestReaderResultId(
                taskId, meterReaderId
        );
        if (resultId == null) {
            throw new ResourceNotFoundException(
                    "任务不存在、不属于当前抄表员或尚未提交结果"
            );
        }
        return buildDetail(requireResultRow(resultId));
    }

    /**
     * 锁定结果后依次写入审核历史、正式记录和任务状态；任意一步失败全部回滚。
     */
    private MeterReadingReviewDecisionVO review(
            Long reviewerId,
            Long resultId,
            Integer resultVersion,
            Integer taskVersion,
            MeterReadingReviewStatus targetStatus,
            BigDecimal confirmedReadingValue,
            String reason
    ) {
        requirePositiveId(reviewerId, "审核人ID不合法");
        requirePositiveId(resultId, "结果ID必须大于0");
        MeterReadingResult result = resultMapper.selectByIdForUpdate(resultId);
        if (result == null) {
            throw new ResourceNotFoundException("抄表结果不存在");
        }
        requireVersion(resultVersion, result.getVersion(), "抄表结果");
        MeterReadingReviewStatus currentStatus =
                MeterReadingReviewStatus.valueOf(result.getReviewStatus());
        if (!currentStatus.canTransitionTo(targetStatus)) {
            throw new IllegalArgumentException("该结果已经完成审核");
        }
        validateConfirmedReading(
                result.getReadingValue(),
                confirmedReadingValue,
                targetStatus,
                reason
        );

        MeterReadingTask task = taskMapper.selectById(result.getTaskId());
        if (task == null) {
            throw new ResourceNotFoundException("关联抄表任务不存在");
        }
        requireVersion(taskVersion, task.getVersion(), "抄表任务");
        if (!MeterReadingTaskStatus.PENDING_REVIEW.name()
                .equals(task.getTaskStatus())) {
            throw new IllegalArgumentException("任务当前不处于待审核状态");
        }

        List<MeterImageRow> images = imageMapper.selectResultImages(resultId);
        if (targetStatus == MeterReadingReviewStatus.APPROVED
                && images.stream().noneMatch(this::isUsableImage)) {
            throw new IllegalArgumentException(
                    "审核通过前至少需要一张有效且已存储的图片"
            );
        }

        LocalDateTime reviewedAt = LocalDateTime.now();
        int resultUpdated = resultMapper.update(
                null,
                Wrappers.<MeterReadingResult>lambdaUpdate()
                        .set(MeterReadingResult::getReviewStatus,
                                targetStatus.name())
                        .set(MeterReadingResult::getConfirmedReadingValue,
                                confirmedReadingValue)
                        .setSql("version = version + 1")
                        .eq(MeterReadingResult::getId, resultId)
                        .eq(MeterReadingResult::getReviewStatus,
                                MeterReadingReviewStatus.PENDING.name())
                        .eq(MeterReadingResult::getVersion, resultVersion)
        );
        if (resultUpdated != 1) {
            throw new VersionConflictException("抄表结果已被其他审核操作修改");
        }

        try {
            reviewMapper.insert(MeterReadingReview.builder()
                    .resultId(resultId)
                    .taskId(task.getId())
                    .reviewAction(targetStatus.name())
                    .reviewerId(reviewerId)
                    .submittedReadingValue(result.getReadingValue())
                    .confirmedReadingValue(confirmedReadingValue)
                    .reviewReason(reason)
                    .reviewedAt(reviewedAt)
                    .build());
        } catch (DuplicateKeyException exception) {
            throw new ResourceConflictException("该结果已经存在审核记录", exception);
        }

        Long recordId = null;
        MeterReadingTaskStatus targetTaskStatus;
        if (targetStatus == MeterReadingReviewStatus.APPROVED) {
            MeterReadingRecord record = MeterReadingRecord.builder()
                    .resultId(resultId)
                    .taskId(task.getId())
                    .meterId(result.getMeterId())
                    .readingValue(confirmedReadingValue)
                    .readingAt(result.getSubmittedAt())
                    .sourceType(result.getSourceType())
                    .executorId(executorId(result))
                    .reviewedBy(reviewerId)
                    .reviewedAt(reviewedAt)
                    .build();
            try {
                recordMapper.insert(record);
            } catch (DuplicateKeyException exception) {
                throw new ResourceConflictException(
                        "该任务已经生成正式抄表记录", exception
                );
            }
            recordId = record.getId();
            targetTaskStatus = MeterReadingTaskStatus.COMPLETED;
        } else {
            targetTaskStatus = MeterReadingTaskStatus.FAILED;
        }

        LambdaUpdateWrapper<MeterReadingTask> taskUpdate =
                Wrappers.<MeterReadingTask>lambdaUpdate()
                        .set(MeterReadingTask::getTaskStatus,
                                targetTaskStatus.name())
                        .set(MeterReadingTask::getCompletedAt,
                                targetTaskStatus == MeterReadingTaskStatus.COMPLETED
                                        ? reviewedAt : null)
                        .set(MeterReadingTask::getFailedReason,
                                targetTaskStatus == MeterReadingTaskStatus.FAILED
                                        ? "审核驳回：" + reason : null)
                        .set(MeterReadingTask::getUpdatedBy, reviewerId)
                        .setSql("version = version + 1")
                        .eq(MeterReadingTask::getId, task.getId())
                        .eq(MeterReadingTask::getTaskStatus,
                                MeterReadingTaskStatus.PENDING_REVIEW.name())
                        .eq(MeterReadingTask::getVersion, taskVersion);
        if (taskMapper.update(null, taskUpdate) != 1) {
            throw new VersionConflictException("抄表任务已被其他操作修改");
        }

        businessNotificationService.notifyReviewDecision(
                resultId,
                task.getId(),
                reviewerId,
                targetStatus == MeterReadingReviewStatus.APPROVED,
                targetStatus == MeterReadingReviewStatus.APPROVED
                        ? "审核已通过，确认读数为" + confirmedReadingValue
                        : "审核已驳回：" + reason
        );

        return new MeterReadingReviewDecisionVO(
                resultId,
                targetStatus,
                result.getReadingValue(),
                confirmedReadingValue,
                resultVersion + 1,
                task.getId(),
                targetTaskStatus,
                taskVersion + 1,
                recordId,
                reviewedAt
        );
    }

    private MeterReadingResultDetailVO buildDetail(MeterReadingResultRow row) {
        List<MeterImageItemVO> images = imageMapper
                .selectResultImages(row.getResultId())
                .stream().map(this::toImageVO).toList();
        List<MeterReadingReviewHistoryVO> history = reviewMapper
                .selectTaskReviewHistory(row.getTaskId())
                .stream().map(this::toHistoryVO).toList();
        TaskExecutorType source = TaskExecutorType.valueOf(row.getSourceType());
        MeterReadingReviewStatus reviewStatus =
                MeterReadingReviewStatus.valueOf(row.getReviewStatus());
        MeterReadingTaskStatus taskStatus =
                MeterReadingTaskStatus.valueOf(row.getTaskStatus());
        return new MeterReadingResultDetailVO(
                row.getResultId(), row.getTaskId(), row.getTaskNo(),
                row.getAttemptNo(), row.getMeterId(), row.getMeterNo(),
                row.getMeterName(), row.getMeterType(), row.getUnit(),
                row.getReadingValue(), row.getConfirmedReadingValue(),
                source, source.getDescription(),
                row.getExecutorId(), row.getExecutorCode(),
                row.getExecutorName(), row.getRecognitionConfidence(),
                row.getRemark(), reviewStatus, reviewStatus.getDescription(),
                taskStatus, taskStatus.getDescription(),
                row.getResultVersion(), row.getTaskVersion(),
                row.getSubmittedAt(), images, history
        );
    }

    private MeterReadingResultListItemVO toListVO(MeterReadingResultRow row) {
        TaskExecutorType source = TaskExecutorType.valueOf(row.getSourceType());
        MeterReadingReviewStatus reviewStatus =
                MeterReadingReviewStatus.valueOf(row.getReviewStatus());
        MeterReadingTaskStatus taskStatus =
                MeterReadingTaskStatus.valueOf(row.getTaskStatus());
        return new MeterReadingResultListItemVO(
                row.getResultId(), row.getTaskId(), row.getTaskNo(),
                row.getAttemptNo(), row.getMeterId(), row.getMeterNo(),
                row.getMeterName(), row.getReadingValue(),
                row.getConfirmedReadingValue(), source,
                source.getDescription(), row.getExecutorId(),
                row.getExecutorName(), reviewStatus,
                reviewStatus.getDescription(), taskStatus,
                taskStatus.getDescription(), row.getResultVersion(),
                row.getTaskVersion(), row.getSubmittedAt()
        );
    }

    private MeterReadingReviewHistoryVO toHistoryVO(
            MeterReadingReviewHistoryRow row
    ) {
        MeterReadingReviewStatus action =
                MeterReadingReviewStatus.valueOf(row.getReviewAction());
        return new MeterReadingReviewHistoryVO(
                row.getReviewId(), row.getResultId(), row.getAttemptNo(),
                action, action.getDescription(), row.getReviewerId(),
                row.getReviewerName(), row.getSubmittedReadingValue(),
                row.getConfirmedReadingValue(), row.getReviewReason(),
                row.getReviewedAt()
        );
    }

    private MeterImageItemVO toImageVO(MeterImageRow row) {
        MeterImageType type = MeterImageType.valueOf(row.getImageType());
        MeterImageStatus status = MeterImageStatus.valueOf(row.getImageStatus());
        return new MeterImageItemVO(
                row.getImageId(), row.getTaskId(), type, type.getDescription(),
                row.getOriginalName(), row.getContentType(), row.getFileSize(),
                row.getImageWidth(), row.getImageHeight(), status,
                status.getDescription(),
                MeterImageStorageStatus.valueOf(row.getStorageStatus()),
                row.getResultId(), row.getVersion(), row.getCreatedAt()
        );
    }

    private boolean isUsableImage(MeterImageRow row) {
        return !Integer.valueOf(1).equals(row.getDeleted())
                && MeterImageStatus.VALID.name().equals(row.getImageStatus())
                && MeterImageStorageStatus.STORED.name()
                .equals(row.getStorageStatus());
    }

    private MeterReadingResultRow requireResultRow(Long resultId) {
        requirePositiveId(resultId, "结果ID必须大于0");
        MeterReadingResultRow row = resultMapper.selectResultDetail(resultId);
        if (row == null) {
            throw new ResourceNotFoundException("抄表结果不存在");
        }
        return row;
    }

    private Long executorId(MeterReadingResult result) {
        return TaskExecutorType.METER_READER.name().equals(result.getSourceType())
                ? result.getMeterReaderId() : result.getDeviceId();
    }

    /**
     * 通过时必须明确确认最终读数；修改原始读数时必须说明原因。
     * 驳回不会产生正式读数，因此确认值必须为空。
     */
    private void validateConfirmedReading(
            BigDecimal submittedReadingValue,
            BigDecimal confirmedReadingValue,
            MeterReadingReviewStatus targetStatus,
            String reason
    ) {
        if (targetStatus == MeterReadingReviewStatus.REJECTED) {
            if (confirmedReadingValue != null) {
                throw new IllegalArgumentException("审核驳回不能填写最终确认读数");
            }
            return;
        }
        if (confirmedReadingValue == null
                || confirmedReadingValue.signum() < 0) {
            throw new IllegalArgumentException("最终确认读数不能为空且不能小于0");
        }
        if (submittedReadingValue.compareTo(confirmedReadingValue) != 0
                && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("修正原始读数时必须填写审核说明");
        }
    }

    private void requireVersion(Integer expected, Integer actual, String name) {
        if (expected == null || expected < 0) {
            throw new IllegalArgumentException(name + "版本不能为空且不能小于0");
        }
        if (!expected.equals(actual)) {
            throw new VersionConflictException(name + "已被其他操作修改");
        }
    }

    private void requirePositiveId(Long id, String message) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private void validateSubmittedRange(MeterReadingResultPageQueryDTO query) {
        if (query.submittedAtStart() != null
                && query.submittedAtEnd() != null
                && query.submittedAtEnd().isBefore(query.submittedAtStart())) {
            throw new IllegalArgumentException("提交结束时间不能早于开始时间");
        }
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
