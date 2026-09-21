package com.byy.meterreading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byy.meterreading.mapper.projection.AiRecognitionTaskDetailRow;
import com.byy.meterreading.mapper.projection.AiRecognitionTaskListRow;
import com.byy.meterreading.model.AiRecognitionTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/** AI 识别任务 Mapper；单表写操作使用 MP，多表查询使用 XML。 */
@Mapper
public interface AiRecognitionTaskMapper
        extends BaseMapper<AiRecognitionTask> {

    IPage<AiRecognitionTaskListRow> selectAdminPage(
            Page<AiRecognitionTaskListRow> page,
            @Param("recognitionNo") String recognitionNo,
            @Param("readingTaskNo") String readingTaskNo,
            @Param("meterNo") String meterNo,
            @Param("status") String status,
            @Param("modelName") String modelName,
            @Param("createdAtStart") LocalDateTime createdAtStart,
            @Param("createdAtEnd") LocalDateTime createdAtEnd
    );

    AiRecognitionTaskDetailRow selectAdminDetail(
            @Param("recognitionTaskId") Long recognitionTaskId
    );

    /** 状态流转事务中锁定识别任务，防止并发回调重复处理。 */
    AiRecognitionTask selectByIdForUpdate(
            @Param("recognitionTaskId") Long recognitionTaskId
    );

    /** 同一图片每次重新创建任务时使用递增的尝试序号。 */
    Integer selectNextAttemptNo(@Param("imageId") Long imageId);
}
