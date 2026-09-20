package com.byy.meterreading.web.controller.device;

import com.byy.meterreading.auth.device.DevicePrincipal;
import com.byy.meterreading.common.result.Result;
import com.byy.meterreading.dto.meterreadingtask.DevicePendingTaskPageQueryDTO;
import com.byy.meterreading.dto.meterreadingtask.MeterReadingTaskVersionDTO;
import com.byy.meterreading.dto.meterreadingtask.SubmitDeviceReadingResultDTO;
import com.byy.meterreading.service.MeterReadingTaskExecutionService;
import com.byy.meterreading.vo.common.PageVO;
import com.byy.meterreading.vo.meterreadingtask.MeterReadingSubmissionVO;
import com.byy.meterreading.vo.meterreadingtask.MeterReadingTaskListItemVO;
import com.byy.meterreading.vo.meterreadingtask.MeterReadingTaskVersionVO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 已通过设备编号和密钥认证的采集设备执行抄表任务的接口。
 */
@RestController
@RequestMapping("/api/v1/device/meter-reading-tasks")
@PreAuthorize("hasRole('DEVICE')")
public class DeviceMeterReadingTaskController {

    private final MeterReadingTaskExecutionService executionService;

    public DeviceMeterReadingTaskController(
            MeterReadingTaskExecutionService executionService
    ) {
        this.executionService = executionService;
    }

    /**
     * 只返回分配给当前认证设备的 PENDING 任务。
     */
    @GetMapping
    public Result<PageVO<MeterReadingTaskListItemVO>> listPendingTasks(
            @AuthenticationPrincipal DevicePrincipal principal,
            @Valid @ModelAttribute DevicePendingTaskPageQueryDTO queryDTO
    ) {
        return Result.success(executionService.listDevicePendingTasks(
                principal.deviceId(),
                queryDTO
        ));
    }

    /**
     * 设备接收本人任务，将 PENDING 推进到 PROCESSING。
     */
    @PostMapping("/{taskId}/accept")
    public Result<MeterReadingTaskVersionVO> acceptTask(
            @AuthenticationPrincipal DevicePrincipal principal,
            @PathVariable Long taskId,
            @Valid @RequestBody MeterReadingTaskVersionDTO versionDTO
    ) {
        return Result.success(executionService.acceptDeviceTask(
                principal.deviceId(),
                taskId,
                versionDTO.version()
        ));
    }

    /**
     * 保存设备识别读数、图片和置信度，并推进到待审核状态。
     */
    @PostMapping("/{taskId}/result")
    public Result<MeterReadingSubmissionVO> submitResult(
            @AuthenticationPrincipal DevicePrincipal principal,
            @PathVariable Long taskId,
            @Valid @RequestBody SubmitDeviceReadingResultDTO resultDTO
    ) {
        return Result.success(executionService.submitDeviceResult(
                principal.deviceId(),
                taskId,
                resultDTO
        ));
    }
}
