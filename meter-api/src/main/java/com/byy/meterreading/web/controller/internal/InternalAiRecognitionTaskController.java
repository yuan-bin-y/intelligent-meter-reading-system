package com.byy.meterreading.web.controller.internal;

import com.byy.meterreading.common.result.Result;
import com.byy.meterreading.dto.airecognition.CompleteAiRecognitionTaskDTO;
import com.byy.meterreading.dto.airecognition.FailAiRecognitionTaskDTO;
import com.byy.meterreading.dto.airecognition.StartAiRecognitionTaskDTO;
import com.byy.meterreading.service.AiRecognitionTaskService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** AI 视觉识别服务回报任务执行状态的内部接口。 */
@RestController
@RequestMapping("/api/v1/internal/ai/recognition-tasks")
@PreAuthorize("hasRole('AI_SERVICE')")
public class InternalAiRecognitionTaskController {

    private final AiRecognitionTaskService recognitionTaskService;

    public InternalAiRecognitionTaskController(
            AiRecognitionTaskService recognitionTaskService
    ) {
        this.recognitionTaskService = recognitionTaskService;
    }

    /** AI 服务收到 MQ 消息后，将任务从待识别推进到识别中。 */
    @PostMapping("/{recognitionTaskId}/start")
    public Result<Void> startRecognition(
            @PathVariable Long recognitionTaskId,
            @Valid @RequestBody StartAiRecognitionTaskDTO startDTO
    ) {
        recognitionTaskService.startTask(recognitionTaskId, startDTO);
        return Result.success(null);
    }

    /** 保存识别读数并生成一条待审核抄表结果。 */
    @PostMapping("/{recognitionTaskId}/result")
    public Result<Void> completeRecognition(
            @PathVariable Long recognitionTaskId,
            @Valid @RequestBody CompleteAiRecognitionTaskDTO completeDTO
    ) {
        recognitionTaskService.completeTask(recognitionTaskId, completeDTO);
        return Result.success(null);
    }

    /** 保存 AI 识别失败编码、原因和处理耗时。 */
    @PostMapping("/{recognitionTaskId}/failure")
    public Result<Void> failRecognition(
            @PathVariable Long recognitionTaskId,
            @Valid @RequestBody FailAiRecognitionTaskDTO failDTO
    ) {
        recognitionTaskService.failTask(recognitionTaskId, failDTO);
        return Result.success(null);
    }
}
