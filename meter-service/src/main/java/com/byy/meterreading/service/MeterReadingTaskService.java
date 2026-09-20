package com.byy.meterreading.service;

import com.byy.meterreading.dto.meterreadingtask.AssignMeterReadingTaskDTO;
import com.byy.meterreading.dto.meterreadingtask.CancelMeterReadingTaskDTO;
import com.byy.meterreading.dto.meterreadingtask.CreateMeterReadingTaskDTO;
import com.byy.meterreading.dto.meterreadingtask.MeterReadingTaskPageQueryDTO;
import com.byy.meterreading.dto.meterreadingtask.MeterReadingTaskVersionDTO;
import com.byy.meterreading.vo.common.PageVO;
import com.byy.meterreading.vo.meterreadingtask.MeterReadingTaskDetailVO;
import com.byy.meterreading.vo.meterreadingtask.MeterReadingTaskListItemVO;
import com.byy.meterreading.vo.meterreadingtask.MeterReadingTaskVersionVO;

/**
 * 抄表任务创建、查询、分配和状态控制业务。
 */
public interface MeterReadingTaskService {

    /**
     * 创建一条已经指定执行者的待执行任务。
     *
     * @param operatorId 当前管理员用户 ID，从 JWT 中取得
     * @param createDTO  表具、执行者、计划时间和备注
     * @return 创建完成后的完整任务详情
     */
    MeterReadingTaskDetailVO createTask(
            Long operatorId,
            CreateMeterReadingTaskDTO createDTO
    );

    /**
     * 按任务关键字、执行者、状态和计划时间分页查询任务。
     *
     * @param queryDTO 分页及筛选条件
     * @return 当前页任务列表和分页信息
     */
    PageVO<MeterReadingTaskListItemVO> listTasks(
            MeterReadingTaskPageQueryDTO queryDTO
    );

    /**
     * 查询指定任务的表具、执行者和完整生命周期信息。
     *
     * @param taskId 任务主键
     * @return 任务详情
     */
    MeterReadingTaskDetailVO getTask(Long taskId);

    /**
     * 为待执行任务更换抄表员或设备。
     *
     * @param operatorId 当前管理员用户 ID
     * @param taskId     任务主键
     * @param assignDTO  新执行者及客户端持有的任务版本
     * @return 保持 PENDING 状态的任务及递增后的版本号
     */
    MeterReadingTaskVersionVO assignTask(
            Long operatorId,
            Long taskId,
            AssignMeterReadingTaskDTO assignDTO
    );

    /**
     * 根据状态机取消允许取消的任务，并记录原因和取消时间。
     *
     * @param operatorId 当前管理员用户 ID
     * @param taskId     任务主键
     * @param cancelDTO  取消原因及客户端持有的任务版本
     * @return CANCELLED 状态及递增后的版本号
     */
    MeterReadingTaskVersionVO cancelTask(
            Long operatorId,
            Long taskId,
            CancelMeterReadingTaskDTO cancelDTO
    );

    /**
     * 将执行失败的任务恢复为待执行状态，并增加重试次数。
     *
     * @param operatorId 当前管理员用户 ID
     * @param taskId     任务主键
     * @param versionDTO 客户端持有的任务版本
     * @return PENDING 状态及递增后的版本号
     */
    MeterReadingTaskVersionVO retryTask(
            Long operatorId,
            Long taskId,
            MeterReadingTaskVersionDTO versionDTO
    );
}
