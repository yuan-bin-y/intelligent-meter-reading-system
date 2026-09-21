package com.byy.meterreading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byy.meterreading.mapper.projection.MeterReadingTaskDetailRow;
import com.byy.meterreading.mapper.projection.MeterReadingTaskListRow;
import com.byy.meterreading.model.MeterReadingTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 抄表任务 Mapper；简单写操作使用 MyBatis-Plus，关联查询使用 XML。
 */
@Mapper
public interface MeterReadingTaskMapper
        extends BaseMapper<MeterReadingTask> {

    /**
     * 关联任务、表具、用户和设备后执行数据库分页。
     * 执行者类型和任务状态在 Service 中转换为数据库保存的枚举名称。
     *
     * @param page             MyBatis-Plus 分页对象
     * @param keyword          匹配任务编号、表具编号或表具名称
     * @param executorType     METER_READER 或 DEVICE
     * @param taskStatus       任务状态枚举名称
     * @param meterReaderId    指定抄表员；为空时不筛选
     * @param deviceId         指定设备；为空时不筛选
     * @param scheduledAtStart 计划执行时间下限，包含边界
     * @param scheduledAtEnd   计划执行时间上限，包含边界
     * @return 当前页关联查询结果
     */
    IPage<MeterReadingTaskListRow> selectTaskPage(
            Page<MeterReadingTaskListRow> page,
            @Param("keyword") String keyword,
            @Param("executorType") String executorType,
            @Param("taskStatus") String taskStatus,
            @Param("meterReaderId") Long meterReaderId,
            @Param("deviceId") Long deviceId,
            @Param("scheduledAtStart") LocalDateTime scheduledAtStart,
            @Param("scheduledAtEnd") LocalDateTime scheduledAtEnd
    );

    /**
     * 查询一条任务的完整关联信息。
     *
     * @param taskId 任务主键
     * @return 查询投影；不存在时返回 null
     */
    MeterReadingTaskDetailRow selectTaskDetail(
            @Param("taskId") Long taskId
    );

    /** 跨表状态流转事务中锁定任务，防止结果和任务状态并发错位。 */
    MeterReadingTask selectByIdForUpdate(@Param("taskId") Long taskId);
}
