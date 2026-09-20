package com.byy.meterreading.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 抄表任务实体，对应 meter_reading_task 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("meter_reading_task")
public class MeterReadingTask {

    /** 抄表任务主键，由 MySQL 自增生成。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 对外使用的全局唯一任务编号。 */
    private String taskNo;

    /** 本次任务需要抄读的表具主键。 */
    private Long meterId;

    /** 执行者类型：METER_READER、DEVICE。 */
    private String executorType;

    /** 人工任务的抄表员用户主键；设备任务时为 null。 */
    private Long meterReaderId;

    /** 自动任务的采集设备主键；人工任务时为 null。 */
    private Long deviceId;

    /** 任务状态，取值由 MeterReadingTaskStatus 定义。 */
    private String taskStatus;

    /** 计划执行时间。 */
    private LocalDateTime scheduledAt;

    /** 实际开始时间。 */
    private LocalDateTime startedAt;

    /** 抄表结果提交时间。 */
    private LocalDateTime submittedAt;

    /** 审核通过并完成任务的时间。 */
    private LocalDateTime completedAt;

    /** 最近一次执行失败原因。 */
    private String failedReason;

    /** 管理员取消任务时填写的原因；未取消任务为 null。 */
    private String cancelReason;

    /** 任务被取消的时间；未取消任务为 null。 */
    private LocalDateTime cancelledAt;

    /** 已经重新执行的次数。 */
    private Integer retryCount;

    /** MyBatis-Plus 乐观锁版本号。 */
    @Version
    private Integer version;

    /** 管理员备注。 */
    private String remark;

    /** 创建任务的管理员用户主键。 */
    private Long createdBy;

    /** 最后修改任务配置的用户主键。 */
    private Long updatedBy;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
