package com.byy.meterreading.web.controller.admin;

import com.byy.meterreading.common.result.Result;
import com.byy.meterreading.dto.meterreadingtask.AssignMeterReadingTaskDTO;
import com.byy.meterreading.dto.meterreadingtask.CancelMeterReadingTaskDTO;
import com.byy.meterreading.dto.meterreadingtask.CreateMeterReadingTaskDTO;
import com.byy.meterreading.dto.meterreadingtask.MeterReadingTaskPageQueryDTO;
import com.byy.meterreading.dto.meterreadingtask.MeterReadingTaskVersionDTO;
import com.byy.meterreading.service.MeterReadingTaskService;
import com.byy.meterreading.vo.common.PageVO;
import com.byy.meterreading.vo.meterreadingtask.MeterReadingTaskDetailVO;
import com.byy.meterreading.vo.meterreadingtask.MeterReadingTaskListItemVO;
import com.byy.meterreading.vo.meterreadingtask.MeterReadingTaskVersionVO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员创建、查询、分配和控制抄表任务的接口。
 */
@RestController
@RequestMapping("/api/v1/admin/meter-reading-tasks")
public class AdminMeterReadingTaskController {

    private final MeterReadingTaskService meterReadingTaskService;

    public AdminMeterReadingTaskController(
            MeterReadingTaskService meterReadingTaskService
    ) {
        this.meterReadingTaskService = meterReadingTaskService;
    }

    /**
     * 创建一条已经指定抄表员或设备的待执行任务。
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<MeterReadingTaskDetailVO> createTask(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateMeterReadingTaskDTO createDTO
    ) {
        return Result.success(meterReadingTaskService.createTask(
                extractUserId(jwt),
                createDTO
        ));
    }

    /**
     * 按任务、表具、执行者、状态和计划时间分页查询任务。
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<PageVO<MeterReadingTaskListItemVO>> listTasks(
            @Valid @ModelAttribute MeterReadingTaskPageQueryDTO queryDTO
    ) {
        return Result.success(meterReadingTaskService.listTasks(queryDTO));
    }

    /**
     * 查询一条抄表任务的完整信息。
     */
    @GetMapping("/{taskId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<MeterReadingTaskDetailVO> getTask(
            @PathVariable Long taskId
    ) {
        return Result.success(meterReadingTaskService.getTask(taskId));
    }

    /**
     * 为待执行任务更换抄表员或设备。
     */
    @PutMapping("/{taskId}/assignment")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<MeterReadingTaskVersionVO> assignTask(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long taskId,
            @Valid @RequestBody AssignMeterReadingTaskDTO assignDTO
    ) {
        return Result.success(meterReadingTaskService.assignTask(
                extractUserId(jwt),
                taskId,
                assignDTO
        ));
    }

    /**
     * 取消待执行、执行中或执行失败的任务。
     */
    @PutMapping("/{taskId}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<MeterReadingTaskVersionVO> cancelTask(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long taskId,
            @Valid @RequestBody CancelMeterReadingTaskDTO cancelDTO
    ) {
        return Result.success(meterReadingTaskService.cancelTask(
                extractUserId(jwt),
                taskId,
                cancelDTO
        ));
    }

    /**
     * 将执行失败的任务恢复为待执行并增加重试次数。
     */
    @PostMapping("/{taskId}/retry")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<MeterReadingTaskVersionVO> retryTask(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long taskId,
            @Valid @RequestBody MeterReadingTaskVersionDTO versionDTO
    ) {
        return Result.success(meterReadingTaskService.retryTask(
                extractUserId(jwt),
                taskId,
                versionDTO
        ));
    }

    /**
     * 从已经通过认证的管理员 JWT 中提取当前用户 ID。
     */
    private Long extractUserId(Jwt jwt) {
        Object userIdClaim = jwt.getClaim("userId");
        if (!(userIdClaim instanceof Number userId)) {
            throw new AuthenticationCredentialsNotFoundException(
                    "JWT 中缺少有效的 userId"
            );
        }
        return userId.longValue();
    }
}
