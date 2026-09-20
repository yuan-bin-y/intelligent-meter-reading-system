package com.byy.meterreading.service;

import com.byy.meterreading.dto.meterreadingrecord.MeterReadingRecordPageQueryDTO;
import com.byy.meterreading.vo.common.PageVO;
import com.byy.meterreading.vo.meterreadingrecord.MeterReadingRecordDetailVO;
import com.byy.meterreading.vo.meterreadingrecord.MeterReadingRecordListItemVO;

/** 管理员和居民查询审核通过后的正式抄表记录。 */
public interface MeterReadingRecordService {
    PageVO<MeterReadingRecordListItemVO> listAdminRecords(
            MeterReadingRecordPageQueryDTO queryDTO
    );

    MeterReadingRecordDetailVO getAdminRecord(Long recordId);

    PageVO<MeterReadingRecordListItemVO> listResidentRecords(
            Long residentId,
            MeterReadingRecordPageQueryDTO queryDTO
    );

    MeterReadingRecordDetailVO getResidentRecord(
            Long residentId,
            Long recordId
    );
}
