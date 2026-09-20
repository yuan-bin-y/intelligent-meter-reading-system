package com.byy.meterreading.mapper.projection;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 抄表任务、表具和执行者关联查询的完整数据库投影。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeterReadingTaskDetailRow {

    /** 任务基本信息。 */
    private Long taskId;
    private String taskNo;

    /** 关联表具信息。 */
    private Long meterId;
    private String meterNo;
    private String meterName;
    private String meterType;
    private String displayType;
    private String unit;

    /**
     * 统一后的执行者信息：人工任务来自 sys_user，设备任务来自 device。
     */
    private String executorType;
    private Long executorId;
    private String executorCode;
    private String executorName;

    /** 任务状态和生命周期时间。 */
    private String taskStatus;
    private LocalDateTime scheduledAt;
    private LocalDateTime startedAt;
    private LocalDateTime submittedAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;

    /** 失败、取消、重试和并发控制信息。 */
    private String failedReason;
    private String cancelReason;
    private Integer retryCount;
    private Integer version;
    private String remark;

    /** 创建及最后修改审计信息。 */
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
