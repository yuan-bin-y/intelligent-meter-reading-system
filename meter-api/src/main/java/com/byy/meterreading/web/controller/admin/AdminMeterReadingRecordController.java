package com.byy.meterreading.web.controller.admin;

import com.byy.meterreading.common.result.Result;
import com.byy.meterreading.dto.meterreadingrecord.MeterReadingRecordPageQueryDTO;
import com.byy.meterreading.service.MeterReadingRecordService;
import com.byy.meterreading.vo.common.PageVO;
import com.byy.meterreading.vo.meterreadingrecord.MeterReadingRecordDetailVO;
import com.byy.meterreading.vo.meterreadingrecord.MeterReadingRecordListItemVO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理员查询不可修改的正式抄表记录。 */
@RestController
@RequestMapping("/api/v1/admin/meter-reading-records")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMeterReadingRecordController {

    private final MeterReadingRecordService recordService;

    public AdminMeterReadingRecordController(
            MeterReadingRecordService recordService
    ) {
        this.recordService = recordService;
    }

    @GetMapping
    public Result<PageVO<MeterReadingRecordListItemVO>> listRecords(
            @Valid @ModelAttribute MeterReadingRecordPageQueryDTO queryDTO
    ) {
        return Result.success(recordService.listAdminRecords(queryDTO));
    }

    @GetMapping("/{recordId}")
    public Result<MeterReadingRecordDetailVO> getRecord(
            @PathVariable Long recordId
    ) {
        return Result.success(recordService.getAdminRecord(recordId));
    }
}
