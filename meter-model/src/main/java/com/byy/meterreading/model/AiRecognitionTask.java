package com.byy.meterreading.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 表具读数识别任务实体，对应 ai_recognition_task 表。
 *
 * <p>该实体保存图片识别过程和识别结果；RabbitMQ 消息发送状态由
 * {@link MqOutboxEvent} 单独记录。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_recognition_task")
public class AiRecognitionTask {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 对外使用的唯一识别任务编号。 */
    private String recognitionNo;

    /** 关联抄表任务、待识别图片和表具。 */
    private Long readingTaskId;
    private Long imageId;
    private Long meterId;

    /** 识别成功后生成的待审核抄表结果。 */
    private Long resultId;

    /** 同一张图片每次重新识别都会生成递增的尝试序号。 */
    private Integer attemptNo;

    /** PENDING、PROCESSING、SUCCEEDED、FAILED 或 CANCELLED。 */
    private String status;

    private BigDecimal recognizedValue;
    private BigDecimal confidence;
    private String modelName;
    private String modelVersion;

    /** AI 服务返回的原始 JSON，便于问题追踪和模型效果分析。 */
    private String rawResult;

    private String failureCode;
    private String failureMessage;
    private Integer retryCount;
    private Integer maxRetryCount;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Long processingDurationMs;

    /** 管理员取消任务时记录完整的操作信息。 */
    private String cancelReason;
    private Long cancelledBy;
    private LocalDateTime cancelledAt;

    @Version
    private Integer version;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
