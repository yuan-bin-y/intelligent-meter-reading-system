package com.byy.meterreading.web.controller.admin;

import com.byy.meterreading.common.result.Result;
import com.byy.meterreading.dto.audit.OperationLogPageQueryDTO;
import com.byy.meterreading.service.OperationLogService;
import com.byy.meterreading.vo.audit.OperationLogDetailVO;
import com.byy.meterreading.vo.audit.OperationLogListVO;
import com.byy.meterreading.vo.common.PageVO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理员查询后台操作审计日志的只读接口。 */
@RestController
@RequestMapping("/api/v1/admin/operation-logs")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOperationLogController {

    private final OperationLogService operationLogService;

    public AdminOperationLogController(
            OperationLogService operationLogService
    ) {
        this.operationLogService = operationLogService;
    }

    /** 按追踪标识、操作人、业务模块、结果和时间范围分页查询。 */
    @GetMapping
    public Result<PageVO<OperationLogListVO>> listOperationLogs(
            @Valid @ModelAttribute OperationLogPageQueryDTO queryDTO
    ) {
        return Result.success(operationLogService.listLogs(queryDTO));
    }

    /** 查询单条审计日志的请求信息、脱敏参数和失败原因。 */
    @GetMapping("/{logId}")
    public Result<OperationLogDetailVO> getOperationLog(
            @PathVariable Long logId
    ) {
        return Result.success(operationLogService.getLog(logId));
    }
}
