package com.byy.meterreading.service;

import com.byy.meterreading.dto.meterreadingtask.DevicePendingTaskPageQueryDTO;
import com.byy.meterreading.dto.meterreadingtask.MyMeterReadingTaskPageQueryDTO;
import com.byy.meterreading.dto.meterreadingtask.SubmitDeviceReadingResultDTO;
import com.byy.meterreading.dto.meterreadingtask.SubmitManualReadingResultDTO;
import com.byy.meterreading.vo.common.PageVO;
import com.byy.meterreading.vo.meterreadingtask.MeterReadingSubmissionVO;
import com.byy.meterreading.vo.meterreadingtask.MeterReadingTaskDetailVO;
import com.byy.meterreading.vo.meterreadingtask.MeterReadingTaskListItemVO;
import com.byy.meterreading.vo.meterreadingtask.MeterReadingTaskVersionVO;

/**
 * 抄表员和设备执行任务的统一业务入口。
 *
 * <p>两个执行端使用不同认证方式和接口路径，但共用任务归属校验、
 * 状态机、乐观锁以及结果提交事务。</p>
 */
public interface MeterReadingTaskExecutionService {

    PageVO<MeterReadingTaskListItemVO> listReaderTasks(
            Long meterReaderId,
            MyMeterReadingTaskPageQueryDTO queryDTO
    );

    MeterReadingTaskDetailVO getReaderTask(
            Long meterReaderId,
            Long taskId
    );

    MeterReadingTaskVersionVO startReaderTask(
            Long meterReaderId,
            Long taskId,
            Integer version
    );

    MeterReadingSubmissionVO submitManualResult(
            Long meterReaderId,
            Long taskId,
            SubmitManualReadingResultDTO resultDTO
    );

    PageVO<MeterReadingTaskListItemVO> listDevicePendingTasks(
            Long deviceId,
            DevicePendingTaskPageQueryDTO queryDTO
    );

    MeterReadingTaskVersionVO acceptDeviceTask(
            Long deviceId,
            Long taskId,
            Integer version
    );

    MeterReadingSubmissionVO submitDeviceResult(
            Long deviceId,
            Long taskId,
            SubmitDeviceReadingResultDTO resultDTO
    );
}
