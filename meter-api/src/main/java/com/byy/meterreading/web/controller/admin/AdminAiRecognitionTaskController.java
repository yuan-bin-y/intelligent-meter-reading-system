package com.byy.meterreading.web.controller.admin;

import com.byy.meterreading.common.result.Result;
import com.byy.meterreading.dto.airecognition.AiRecognitionTaskPageQueryDTO;
import com.byy.meterreading.dto.airecognition.CancelAiRecognitionTaskDTO;
import com.byy.meterreading.dto.airecognition.RetryAiRecognitionTaskDTO;
import com.byy.meterreading.service.AiRecognitionTaskService;
import com.byy.meterreading.vo.airecognition.AiRecognitionTaskActionVO;
import com.byy.meterreading.vo.airecognition.AiRecognitionTaskDetailVO;
import com.byy.meterreading.vo.airecognition.AiRecognitionTaskListVO;
import com.byy.meterreading.vo.common.PageVO;
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

/** 管理员创建、查询和治理 AI 识别任务的接口。 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAiRecognitionTaskController {

    private final AiRecognitionTaskService recognitionTaskService;

    public AdminAiRecognitionTaskController(
            AiRecognitionTaskService recognitionTaskService
    ) {
        this.recognitionTaskService = recognitionTaskService;
    }

    /** 对一张有效且已存入 OSS 的抄表图片手动发起识别。 */
    @PostMapping("/meter-images/{imageId}/recognition-tasks")
    public Result<AiRecognitionTaskActionVO> createRecognitionTask(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long imageId
    ) {
        return Result.success(recognitionTaskService.createManualTask(
                extractUserId(jwt), imageId
        ));
    }

    /** 分页查询识别任务及其主要识别结果。 */
    @GetMapping("/recognition-tasks")
    public Result<PageVO<AiRecognitionTaskListVO>> listRecognitionTasks(
            @Valid @ModelAttribute AiRecognitionTaskPageQueryDTO queryDTO
    ) {
        return Result.success(
                recognitionTaskService.listAdminTasks(queryDTO)
        );
    }

    /** 查询识别任务、业务关联及其最新 Outbox 消息状态。 */
    @GetMapping("/recognition-tasks/{recognitionTaskId}")
    public Result<AiRecognitionTaskDetailVO> getRecognitionTask(
            @PathVariable Long recognitionTaskId
    ) {
        return Result.success(
                recognitionTaskService.getAdminTask(recognitionTaskId)
        );
    }

    /** 将失败任务恢复为待识别，并创建一条新的 Outbox 消息。 */
    @PostMapping("/recognition-tasks/{recognitionTaskId}/retry")
    public Result<AiRecognitionTaskActionVO> retryRecognitionTask(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long recognitionTaskId,
            @Valid @RequestBody RetryAiRecognitionTaskDTO retryDTO
    ) {
        return Result.success(recognitionTaskService.retryTask(
                extractUserId(jwt), recognitionTaskId, retryDTO
        ));
    }

    /** 取消待识别或识别中的任务，并记录管理员与取消原因。 */
    @PutMapping("/recognition-tasks/{recognitionTaskId}/cancel")
    public Result<AiRecognitionTaskActionVO> cancelRecognitionTask(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long recognitionTaskId,
            @Valid @RequestBody CancelAiRecognitionTaskDTO cancelDTO
    ) {
        return Result.success(recognitionTaskService.cancelTask(
                extractUserId(jwt), recognitionTaskId, cancelDTO
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
