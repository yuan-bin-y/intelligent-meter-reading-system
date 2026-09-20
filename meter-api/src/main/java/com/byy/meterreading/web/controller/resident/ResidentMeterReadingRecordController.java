package com.byy.meterreading.web.controller.resident;

import com.byy.meterreading.common.result.Result;
import com.byy.meterreading.dto.meterreadingrecord.MeterReadingRecordPageQueryDTO;
import com.byy.meterreading.service.MeterReadingRecordService;
import com.byy.meterreading.vo.common.PageVO;
import com.byy.meterreading.vo.meterreadingrecord.MeterReadingRecordDetailVO;
import com.byy.meterreading.vo.meterreadingrecord.MeterReadingRecordListItemVO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 居民只查询本人已绑定表具的正式抄表记录。 */
@RestController
@RequestMapping("/api/v1/resident/meter-reading-records")
@PreAuthorize("hasRole('RESIDENT')")
public class ResidentMeterReadingRecordController {

    private final MeterReadingRecordService recordService;

    public ResidentMeterReadingRecordController(
            MeterReadingRecordService recordService
    ) {
        this.recordService = recordService;
    }

    @GetMapping
    public Result<PageVO<MeterReadingRecordListItemVO>> listRecords(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @ModelAttribute MeterReadingRecordPageQueryDTO queryDTO
    ) {
        return Result.success(recordService.listResidentRecords(
                extractUserId(jwt), queryDTO
        ));
    }

    @GetMapping("/{recordId}")
    public Result<MeterReadingRecordDetailVO> getRecord(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long recordId
    ) {
        return Result.success(recordService.getResidentRecord(
                extractUserId(jwt), recordId
        ));
    }

    private Long extractUserId(Jwt jwt) {
        Object userIdClaim = jwt.getClaim("userId");
        if (!(userIdClaim instanceof Number userId)) {
            throw new AuthenticationCredentialsNotFoundException(
                    "JWT 中缺少有效的 userId"
            );
        }
        return userId.longValue();
    }
}
