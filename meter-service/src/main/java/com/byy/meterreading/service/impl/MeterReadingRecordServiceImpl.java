package com.byy.meterreading.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byy.meterreading.common.exception.ResourceNotFoundException;
import com.byy.meterreading.dto.meterreadingrecord.MeterReadingRecordPageQueryDTO;
import com.byy.meterreading.mapper.MeterImageMapper;
import com.byy.meterreading.mapper.MeterReadingRecordMapper;
import com.byy.meterreading.mapper.projection.MeterImageRow;
import com.byy.meterreading.mapper.projection.MeterReadingRecordRow;
import com.byy.meterreading.model.enums.MeterImageStatus;
import com.byy.meterreading.model.enums.MeterImageStorageStatus;
import com.byy.meterreading.model.enums.MeterImageType;
import com.byy.meterreading.model.enums.TaskExecutorType;
import com.byy.meterreading.service.MeterReadingRecordService;
import com.byy.meterreading.vo.common.PageVO;
import com.byy.meterreading.vo.meterimage.MeterImageItemVO;
import com.byy.meterreading.vo.meterreadingrecord.MeterReadingRecordDetailVO;
import com.byy.meterreading.vo.meterreadingrecord.MeterReadingRecordListItemVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 正式记录查询实现，居民归属条件直接进入 SQL。 */
@Service
public class MeterReadingRecordServiceImpl
        implements MeterReadingRecordService {

    private final MeterReadingRecordMapper recordMapper;
    private final MeterImageMapper imageMapper;

    public MeterReadingRecordServiceImpl(
            MeterReadingRecordMapper recordMapper,
            MeterImageMapper imageMapper
    ) {
        this.recordMapper = recordMapper;
        this.imageMapper = imageMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageVO<MeterReadingRecordListItemVO> listAdminRecords(
            MeterReadingRecordPageQueryDTO queryDTO
    ) {
        return listRecords(null, queryDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public MeterReadingRecordDetailVO getAdminRecord(Long recordId) {
        return getRecord(null, recordId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageVO<MeterReadingRecordListItemVO> listResidentRecords(
            Long residentId,
            MeterReadingRecordPageQueryDTO queryDTO
    ) {
        requirePositiveId(residentId, "居民ID不合法");
        return listRecords(residentId, queryDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public MeterReadingRecordDetailVO getResidentRecord(
            Long residentId,
            Long recordId
    ) {
        requirePositiveId(residentId, "居民ID不合法");
        return getRecord(residentId, recordId);
    }

    private PageVO<MeterReadingRecordListItemVO> listRecords(
            Long residentId,
            MeterReadingRecordPageQueryDTO queryDTO
    ) {
        validateReadingRange(queryDTO);
        Page<MeterReadingRecordRow> page = new Page<>(
                queryDTO.page(), queryDTO.pageSize()
        );
        IPage<MeterReadingRecordRow> result = recordMapper.selectRecordPage(
                page,
                residentId,
                queryDTO.keyword(),
                queryDTO.meterId(),
                enumName(queryDTO.sourceType()),
                queryDTO.readingAtStart(),
                queryDTO.readingAtEnd()
        );
        return new PageVO<>(
                result.getRecords().stream().map(this::toListVO).toList(),
                result.getTotal(), result.getCurrent(), result.getSize()
        );
    }

    private MeterReadingRecordDetailVO getRecord(
            Long residentId,
            Long recordId
    ) {
        requirePositiveId(recordId, "正式记录ID必须大于0");
        MeterReadingRecordRow row = recordMapper.selectRecordDetail(
                recordId, residentId
        );
        if (row == null) {
            throw new ResourceNotFoundException(
                    residentId == null
                            ? "正式抄表记录不存在"
                            : "正式抄表记录不存在或不属于当前居民"
            );
        }
        List<MeterImageItemVO> images = imageMapper
                .selectResultImages(row.getResultId())
                .stream()
                .filter(this::isVisibleImage)
                .map(this::toImageVO)
                .toList();
        TaskExecutorType source = TaskExecutorType.valueOf(row.getSourceType());
        return new MeterReadingRecordDetailVO(
                row.getRecordId(), row.getResultId(), row.getTaskId(),
                row.getTaskNo(), row.getAttemptNo(), row.getMeterId(),
                row.getMeterNo(), row.getMeterName(), row.getMeterType(),
                row.getUnit(), row.getReadingValue(), row.getReadingAt(),
                source, source.getDescription(), row.getExecutorId(),
                row.getExecutorCode(), row.getExecutorName(),
                row.getReviewerId(), row.getReviewerName(),
                row.getReviewedAt(), images
        );
    }

    private MeterReadingRecordListItemVO toListVO(MeterReadingRecordRow row) {
        TaskExecutorType source = TaskExecutorType.valueOf(row.getSourceType());
        return new MeterReadingRecordListItemVO(
                row.getRecordId(), row.getResultId(), row.getTaskId(),
                row.getTaskNo(), row.getMeterId(), row.getMeterNo(),
                row.getMeterName(), row.getMeterType(), row.getUnit(),
                row.getReadingValue(), row.getReadingAt(), source,
                source.getDescription(), row.getExecutorId(),
                row.getExecutorName(), row.getReviewerId(),
                row.getReviewerName(), row.getReviewedAt()
        );
    }

    private MeterImageItemVO toImageVO(MeterImageRow row) {
        MeterImageType type = MeterImageType.valueOf(row.getImageType());
        MeterImageStatus status = MeterImageStatus.valueOf(row.getImageStatus());
        return new MeterImageItemVO(
                row.getImageId(), row.getTaskId(), type, type.getDescription(),
                row.getOriginalName(), row.getContentType(), row.getFileSize(),
                row.getImageWidth(), row.getImageHeight(), status,
                status.getDescription(),
                MeterImageStorageStatus.valueOf(row.getStorageStatus()),
                row.getResultId(), row.getVersion(), row.getCreatedAt()
        );
    }

    private boolean isVisibleImage(MeterImageRow row) {
        return !Integer.valueOf(1).equals(row.getDeleted())
                && MeterImageStatus.VALID.name().equals(row.getImageStatus())
                && MeterImageStorageStatus.STORED.name()
                .equals(row.getStorageStatus());
    }

    private void validateReadingRange(MeterReadingRecordPageQueryDTO query) {
        if (query.readingAtStart() != null
                && query.readingAtEnd() != null
                && query.readingAtEnd().isBefore(query.readingAtStart())) {
            throw new IllegalArgumentException("读数结束时间不能早于开始时间");
        }
    }

    private void requirePositiveId(Long id, String message) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
