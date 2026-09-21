package com.byy.meterreading.vo.meterreadingreview;

import com.byy.meterreading.model.enums.MeterReadingReviewStatus;
import com.byy.meterreading.model.enums.MeterReadingTaskStatus;
import com.byy.meterreading.model.enums.TaskExecutorType;
import com.byy.meterreading.vo.meterimage.MeterImageItemVO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 抄表结果、任务、执行者、图片和审核信息的组合详情。 */
public record MeterReadingResultDetailVO(
        Long resultId,
        Long taskId,
        String taskNo,
        Integer attemptNo,
        Long meterId,
        String meterNo,
        String meterName,
        String meterType,
        String unit,
        BigDecimal readingValue,
        BigDecimal confirmedReadingValue,
        TaskExecutorType sourceType,
        String sourceTypeName,
        Long executorId,
        String executorCode,
        String executorName,
        BigDecimal recognitionConfidence,
        String remark,
        MeterReadingReviewStatus reviewStatus,
        String reviewStatusName,
        MeterReadingTaskStatus taskStatus,
        String taskStatusName,
        Integer resultVersion,
        Integer taskVersion,
        LocalDateTime submittedAt,
        List<MeterImageItemVO> images,
        List<MeterReadingReviewHistoryVO> reviewHistory
) {
    public MeterReadingResultDetailVO {
        images = images == null ? List.of() : List.copyOf(images);
        reviewHistory = reviewHistory == null
                ? List.of()
                : List.copyOf(reviewHistory);
    }
}
