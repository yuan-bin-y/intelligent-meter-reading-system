package com.byy.meterreading.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 后台操作审计实体，对应 operation_log 表。
 * 审计记录只允许新增，用于追踪操作人、业务动作及最终执行结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("operation_log")
public class OperationLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 与接口响应、MDC 运行日志关联的请求追踪标识。 */
    private String traceId;

    /** 操作人用户 ID；系统操作可以为空。 */
    private Long operatorId;

    /** 操作发生时的用户名快照。 */
    private String operatorUsername;

    private String module;
    private String action;
    private String resourceType;
    private Long resourceId;
    private String requestMethod;
    private String requestPath;
    private String clientIp;

    /** 已完成敏感字段脱敏和长度截断的请求参数。 */
    private String requestParams;

    /** OperationResult 的枚举名称：SUCCESS 或 FAILED。 */
    private String result;

    /** 失败时可安全对外展示的错误说明，成功时为空。 */
    private String errorMessage;

    private Long durationMs;
    private LocalDateTime createdAt;
}
