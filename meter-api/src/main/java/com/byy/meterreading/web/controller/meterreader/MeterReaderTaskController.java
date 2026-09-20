package com.byy.meterreading.web.controller.meterreader;

import com.byy.meterreading.common.result.Result;
import com.byy.meterreading.dto.meterreadingtask.MeterReadingTaskVersionDTO;
import com.byy.meterreading.dto.meterreadingtask.MyMeterReadingTaskPageQueryDTO;
import com.byy.meterreading.dto.meterreadingtask.SubmitManualReadingResultDTO;
import com.byy.meterreading.service.MeterReadingTaskExecutionService;
import com.byy.meterreading.vo.common.PageVO;
import com.byy.meterreading.vo.meterreadingtask.MeterReadingSubmissionVO;
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
 * 抄表员查询和执行本人任务的接口。
 */
@RestController
@RequestMapping("/api/v1/meter-reader/tasks")
@PreAuthorize("hasRole('METER_READER')")
public class MeterReaderTaskController {

    private final MeterReadingTaskExecutionService executionService;

    public MeterReaderTaskController(
            MeterReadingTaskExecutionService executionService
    ) {
        this.executionService = executionService;
    }

    /**
     * JWT 中的 userId 会作为固定归属条件，调用方不能查询其他抄表员任务。
     */
    @GetMapping
    public Result<PageVO<MeterReadingTaskListItemVO>> listMyTasks(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @ModelAttribute MyMeterReadingTaskPageQueryDTO queryDTO
    ) {
        return Result.success(executionService.listReaderTasks(
                extractUserId(jwt),
                queryDTO
        ));
    }

    @GetMapping("/{taskId}")
    public Result<MeterReadingTaskDetailVO> getMyTask(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long taskId
    ) {
        return Result.success(executionService.getReaderTask(
                extractUserId(jwt),
                taskId
        ));
    }

    /**
     * 将本人待执行任务从 PENDING 推进到 PROCESSING。
     */
    @PutMapping("/{taskId}/start")
    public Result<MeterReadingTaskVersionVO> startTask(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long taskId,
            @Valid @RequestBody MeterReadingTaskVersionDTO versionDTO
    ) {
        return Result.success(executionService.startReaderTask(
                extractUserId(jwt),
                taskId,
                versionDTO.version()
        ));
    }

    /**
     * 保存人工读数和照片，并把任务推进到 PENDING_REVIEW。
     */
    @PostMapping("/{taskId}/result")
    public Result<MeterReadingSubmissionVO> submitResult(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long taskId,
            @Valid @RequestBody SubmitManualReadingResultDTO resultDTO
    ) {
        return Result.success(executionService.submitManualResult(
                extractUserId(jwt),
                taskId,
                resultDTO
        ));
    }

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
