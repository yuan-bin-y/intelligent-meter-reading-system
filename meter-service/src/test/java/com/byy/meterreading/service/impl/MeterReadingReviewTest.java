package com.byy.meterreading.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.byy.meterreading.common.exception.VersionConflictException;
import com.byy.meterreading.dto.meterreadingreview.ApproveMeterReadingResultDTO;
import com.byy.meterreading.mapper.MeterImageMapper;
import com.byy.meterreading.mapper.MeterReadingRecordMapper;
import com.byy.meterreading.mapper.MeterReadingResultMapper;
import com.byy.meterreading.mapper.MeterReadingReviewMapper;
import com.byy.meterreading.mapper.MeterReadingTaskMapper;
import com.byy.meterreading.mapper.projection.MeterImageRow;
import com.byy.meterreading.model.MeterReadingRecord;
import com.byy.meterreading.model.MeterReadingResult;
import com.byy.meterreading.model.MeterReadingTask;
import com.byy.meterreading.service.BusinessNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MeterReadingReviewTest {
    private final MeterReadingResultMapper results = mock(MeterReadingResultMapper.class);
    private final MeterReadingTaskMapper tasks = mock(MeterReadingTaskMapper.class);
    private final MeterReadingReviewMapper reviews = mock(MeterReadingReviewMapper.class);
    private final MeterReadingRecordMapper records = mock(MeterReadingRecordMapper.class);
    private final MeterImageMapper images = mock(MeterImageMapper.class);
    private final BusinessNotificationService notifications = mock(BusinessNotificationService.class);
    private final MeterReadingReviewServiceImpl service = new MeterReadingReviewServiceImpl(
            results, tasks, reviews, records, images, notifications);

    @BeforeAll
    static void initializeMapperMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, MeterReadingResult.class);
        TableInfoHelper.initTableInfo(assistant, MeterReadingTask.class);
    }

    @BeforeEach
    void setUp() {
        when(results.selectByIdForUpdate(1L)).thenReturn(MeterReadingResult.builder()
                .id(1L).taskId(2L).reviewStatus("PENDING").version(3)
                .readingValue(new BigDecimal("92.17")).sourceType("METER_READER")
                .meterReaderId(9L).meterId(5L).build());
        when(tasks.selectById(2L)).thenReturn(MeterReadingTask.builder()
                .id(2L).taskStatus("PENDING_REVIEW").version(4).build());
    }

    @Test
    void staleResultVersionCannotGenerateFormalReading() {
        assertThrows(VersionConflictException.class, () -> service.approve(
                7L, 1L, new ApproveMeterReadingResultDTO(2, 4, new BigDecimal("92.17"), null)));
        verifyNoInteractions(reviews, records, notifications);
    }

    @Test
    void alreadyReviewedResultCannotBeApprovedAgain() {
        when(results.selectByIdForUpdate(1L)).thenReturn(MeterReadingResult.builder()
                .id(1L).taskId(2L).reviewStatus("APPROVED").version(3).build());
        assertThrows(IllegalArgumentException.class, () -> service.approve(7L, 1L, approval()));
        verifyNoInteractions(reviews, records, notifications);
    }

    @Test
    void approvalRequiresAtLeastOneUsableImage() {
        when(images.selectResultImages(1L)).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class, () -> service.approve(7L, 1L, approval()));
        verifyNoInteractions(reviews, records, notifications);
    }

    @Test
    void approvalCreatesFormalReadingAndCompletesTheTask() {
        MeterImageRow image = new MeterImageRow();
        image.setImageStatus("VALID");
        image.setStorageStatus("STORED");
        image.setDeleted(0);
        when(images.selectResultImages(1L)).thenReturn(List.of(image));
        when(results.update(isNull(), ArgumentMatchers.<Wrapper<MeterReadingResult>>any()))
                .thenReturn(1);
        when(tasks.update(isNull(), ArgumentMatchers.<Wrapper<MeterReadingTask>>any()))
                .thenReturn(1);
        var decision = service.approve(7L, 1L, approval());
        ArgumentCaptor<MeterReadingRecord> record = ArgumentCaptor.forClass(MeterReadingRecord.class);
        verify(records).insert(record.capture());
        assertEquals(1L, record.getValue().getResultId());
        assertEquals(2L, record.getValue().getTaskId());
        assertEquals(new BigDecimal("92.17"), record.getValue().getReadingValue());
        assertEquals("COMPLETED", decision.taskStatus().name());
        assertEquals(5, decision.taskVersion());
    }

    private ApproveMeterReadingResultDTO approval() {
        return new ApproveMeterReadingResultDTO(3, 4, new BigDecimal("92.17"), null);
    }
}
