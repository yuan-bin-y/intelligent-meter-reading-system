package com.byy.meterreading.aisimulator.recognition;

import java.math.BigDecimal;

/** 一次视觉识别的业务结果。 */
public sealed interface RecognitionOutcome {

    /** 模型成功读取表盘数值。 */
    record Success(
            BigDecimal recognizedValue,
            BigDecimal confidence,
            String rawResult
    ) implements RecognitionOutcome {
    }

    /** 图片模糊等可确定的业务失败，不需要 MQ 重试。 */
    record Failure(
            String failureCode,
            String failureMessage
    ) implements RecognitionOutcome {
    }
}
