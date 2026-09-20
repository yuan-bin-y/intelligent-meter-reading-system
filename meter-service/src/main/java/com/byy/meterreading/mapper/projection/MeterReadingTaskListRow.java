package com.byy.meterreading.mapper.projection;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 管理员分页查询抄表任务的数据库投影。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeterReadingTaskListRow {

    /** 任务表主键。 */
    private Long taskId;
    /** 对外展示的唯一任务编号。 */
    private String taskNo;
    /** 关联表具主键。 */
    private Long meterId;
    private String meterNo;
    private String meterName;
    /** 数据库存储的执行者类型枚举名称。 */
    private String executorType;
    /** SQL 根据执行者类型合并得到的用户 ID 或设备 ID。 */
    private Long executorId;
    /** SQL 根据执行者类型取得的用户显示名称或设备名称。 */
    private String executorName;
    /** 数据库存储的任务状态枚举名称。 */
    private String taskStatus;
    private LocalDateTime scheduledAt;
    private Integer retryCount;
    /** 客户端后续写操作需要携带的乐观锁版本。 */
    private Integer version;
    private LocalDateTime updatedAt;
}
