package com.byy.meterreading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byy.meterreading.mapper.projection.MeterImageRow;
import com.byy.meterreading.model.MeterImage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 图片元数据 Mapper；关联查询和并发关键 SQL 使用 XML。 */
@Mapper
public interface MeterImageMapper extends BaseMapper<MeterImage> {

    IPage<MeterImageRow> selectImagePage(
            Page<MeterImageRow> page,
            @Param("taskNo") String taskNo,
            @Param("meterNo") String meterNo,
            @Param("uploaderType") String uploaderType,
            @Param("uploaderId") Long uploaderId,
            @Param("imageStatus") String imageStatus,
            @Param("storageStatus") String storageStatus,
            @Param("bound") Boolean bound,
            @Param("deleted") Boolean deleted,
            @Param("createdAtStart") LocalDateTime createdAtStart,
            @Param("createdAtEnd") LocalDateTime createdAtEnd
    );

    MeterImageRow selectImageDetail(@Param("imageId") Long imageId);

    List<MeterImageRow> selectTaskImages(
            @Param("taskId") Long taskId,
            @Param("includeDeleted") boolean includeDeleted
    );

    /** 结果提交事务中锁定全部图片，防止并发删除或重复绑定。 */
    List<MeterImage> selectForSubmission(
            @Param("imageIds") List<Long> imageIds
    );

    /** 只有仍未绑定结果的本人图片可以软删除。 */
    int softDeleteReaderImage(
            @Param("imageId") Long imageId,
            @Param("taskId") Long taskId,
            @Param("readerId") Long readerId,
            @Param("version") Integer version,
            @Param("deletedAt") LocalDateTime deletedAt
    );

    List<MeterImage> selectCleanupCandidates(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("limit") int limit
    );
}
