package com.byy.meterreading.service;

import com.byy.meterreading.dto.airecognition.AiRecognitionTaskPageQueryDTO;
import com.byy.meterreading.dto.airecognition.CancelAiRecognitionTaskDTO;
import com.byy.meterreading.dto.airecognition.CompleteAiRecognitionTaskDTO;
import com.byy.meterreading.dto.airecognition.FailAiRecognitionTaskDTO;
import com.byy.meterreading.dto.airecognition.RetryAiRecognitionTaskDTO;
import com.byy.meterreading.dto.airecognition.StartAiRecognitionTaskDTO;
import com.byy.meterreading.vo.airecognition.AiRecognitionTaskActionVO;
import com.byy.meterreading.vo.airecognition.AiRecognitionTaskDetailVO;
import com.byy.meterreading.vo.airecognition.AiRecognitionTaskListVO;
import com.byy.meterreading.vo.common.PageVO;

/**
 * AI 识别任务创建、查询、治理及视觉服务回调业务。
 *
 * <p>创建和重试任务时，识别任务与 Outbox 事件必须在同一个数据库事务中保存。</p>
 */
public interface AiRecognitionTaskService {

    /** 设备图片成功保存后，由图片业务内部自动创建识别任务。 */
    AiRecognitionTaskActionVO createAutomaticTask(Long imageId);

    /** 管理员对指定图片手动发起一次新的识别。 */
    AiRecognitionTaskActionVO createManualTask(
            Long adminId,
            Long imageId
    );

    PageVO<AiRecognitionTaskListVO> listAdminTasks(
            AiRecognitionTaskPageQueryDTO queryDTO
    );

    AiRecognitionTaskDetailVO getAdminTask(Long recognitionTaskId);

    /** 将失败任务恢复为待识别，并创建新的 Outbox 事件。 */
    AiRecognitionTaskActionVO retryTask(
            Long adminId,
            Long recognitionTaskId,
            RetryAiRecognitionTaskDTO retryDTO
    );

    AiRecognitionTaskActionVO cancelTask(
            Long adminId,
            Long recognitionTaskId,
            CancelAiRecognitionTaskDTO cancelDTO
    );

    /** AI 服务收到 RabbitMQ 消息后报告开始处理。 */
    void startTask(
            Long recognitionTaskId,
            StartAiRecognitionTaskDTO startDTO
    );

    /** 保存 AI 识别结果，并生成待审核抄表结果。 */
    void completeTask(
            Long recognitionTaskId,
            CompleteAiRecognitionTaskDTO completeDTO
    );

    /** 保存本次 AI 识别失败信息。 */
    void failTask(
            Long recognitionTaskId,
            FailAiRecognitionTaskDTO failDTO
    );
}
