package com.byy.meterreading.web.controller.review;

import com.byy.meterreading.common.audit.OperationAudit;
import com.byy.meterreading.common.result.Result;
import com.byy.meterreading.dto.meterreadingreview.ApproveMeterReadingResultDTO;
import com.byy.meterreading.dto.meterreadingreview.MeterReadingResultPageQueryDTO;
import com.byy.meterreading.dto.meterreadingreview.RejectMeterReadingResultDTO;
import com.byy.meterreading.service.MeterReadingReviewService;
import com.byy.meterreading.vo.common.PageVO;
import com.byy.meterreading.vo.meterreadingreview.MeterReadingResultDetailVO;
import com.byy.meterreading.vo.meterreadingreview.MeterReadingResultListItemVO;
import com.byy.meterreading.vo.meterreadingreview.MeterReadingReviewDecisionVO;
import com.byy.meterreading.vo.meterreadingreview.MeterReadingReviewHistoryVO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 管理员和审核员共用的抄表结果审核接口。 */
@RestController
@RequestMapping("/api/v1/review/meter-reading-results")
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
public class MeterReadingReviewController {

    private final MeterReadingReviewService reviewService;

    public MeterReadingReviewController(
            MeterReadingReviewService reviewService
    ) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public Result<PageVO<MeterReadingResultListItemVO>> listResults(
            @Valid @ModelAttribute MeterReadingResultPageQueryDTO queryDTO
    ) {
        return Result.success(reviewService.listResults(queryDTO));
    }

    @GetMapping("/{resultId}")
    public Result<MeterReadingResultDetailVO> getResult(
            @PathVariable Long resultId
    ) {
        return Result.success(reviewService.getResult(resultId));
    }

    /** 查询指定提交结果本身的审核记录。 */
    @GetMapping("/{resultId}/history")
    public Result<List<MeterReadingReviewHistoryVO>> listReviewHistory(
            @PathVariable Long resultId
    ) {
        return Result.success(reviewService.listReviewHistory(resultId));
    }

    /** 审核通过会生成正式记录并将任务推进为 COMPLETED。 */
    @OperationAudit(
            module = "抄表结果审核",
            action = "审核通过",
            resourceType = "METER_READING_RESULT",
            resourceIdExpression = "#resultId"
    )
    @PutMapping("/{resultId}/approve")
    public Result<MeterReadingReviewDecisionVO> approve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long resultId,
            @Valid @RequestBody ApproveMeterReadingResultDTO approveDTO
    ) {
        return Result.success(reviewService.approve(
                extractUserId(jwt), resultId, approveDTO
        ));
    }

    /** 审核驳回会保留本次结果并将任务推进为 FAILED。 */
    @OperationAudit(
            module = "抄表结果审核",
            action = "审核驳回",
            resourceType = "METER_READING_RESULT",
            resourceIdExpression = "#resultId"
    )
    @PutMapping("/{resultId}/reject")
    public Result<MeterReadingReviewDecisionVO> reject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long resultId,
            @Valid @RequestBody RejectMeterReadingResultDTO rejectDTO
    ) {
        return Result.success(reviewService.reject(
                extractUserId(jwt), resultId, rejectDTO
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
