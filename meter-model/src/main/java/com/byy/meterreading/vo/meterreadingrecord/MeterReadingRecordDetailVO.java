package com.byy.meterreading.vo.meterreadingrecord;

import com.byy.meterreading.model.enums.TaskExecutorType;
import com.byy.meterreading.vo.meterimage.MeterImageItemVO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 正式抄表记录详情及其审核通过图片。 */
public record MeterReadingRecordDetailVO(
        Long recordId,
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
        LocalDateTime readingAt,
        TaskExecutorType sourceType,
        String sourceTypeName,
        Long executorId,
        String executorCode,
        String executorName,
        Long reviewerId,
        String reviewerName,
        LocalDateTime reviewedAt,
        List<MeterImageItemVO> images
) {
    public MeterReadingRecordDetailVO {
        images = images == null ? List.of() : List.copyOf(images);
    }
}
