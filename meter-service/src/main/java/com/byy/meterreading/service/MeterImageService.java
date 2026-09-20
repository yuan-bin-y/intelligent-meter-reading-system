package com.byy.meterreading.service;

import com.byy.meterreading.dto.meterimage.MeterImagePageQueryDTO;
import com.byy.meterreading.dto.meterimage.UpdateMeterImageStatusDTO;
import com.byy.meterreading.vo.common.PageVO;
import com.byy.meterreading.vo.meterimage.MeterImageAccessUrlVO;
import com.byy.meterreading.vo.meterimage.MeterImageDetailVO;
import com.byy.meterreading.vo.meterimage.MeterImageItemVO;
import com.byy.meterreading.vo.meterimage.MeterImageVersionVO;

import java.util.List;
import java.util.Set;

/** 抄表图片上传、查询、状态管理、访问授权和清理业务。 */
public interface MeterImageService {

    MeterImageItemVO uploadReaderImage(
            Long readerId,
            Long taskId,
            MeterImageUploadCommand command
    );

    List<MeterImageItemVO> listReaderTaskImages(
            Long readerId,
            Long taskId
    );

    void deleteReaderImage(
            Long readerId,
            Long taskId,
            Long imageId,
            Integer version
    );

    MeterImageItemVO uploadDeviceImage(
            Long deviceId,
            Long taskId,
            MeterImageUploadCommand command
    );

    PageVO<MeterImageDetailVO> listAdminImages(
            MeterImagePageQueryDTO queryDTO
    );

    MeterImageDetailVO getAdminImage(Long imageId);

    List<MeterImageDetailVO> listAdminTaskImages(Long taskId);

    MeterImageVersionVO invalidateImage(
            Long operatorId,
            Long imageId,
            UpdateMeterImageStatusDTO statusDTO
    );

    MeterImageVersionVO restoreImage(
            Long operatorId,
            Long imageId,
            UpdateMeterImageStatusDTO statusDTO
    );

    MeterImageAccessUrlVO getAccessUrl(
            Long currentUserId,
            Set<String> roles,
            Long imageId
    );

    void cleanupExpiredImages();
}
