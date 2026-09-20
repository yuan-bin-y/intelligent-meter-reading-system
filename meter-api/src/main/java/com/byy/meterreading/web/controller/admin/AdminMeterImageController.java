package com.byy.meterreading.web.controller.admin;

import com.byy.meterreading.common.result.Result;
import com.byy.meterreading.dto.meterimage.MeterImagePageQueryDTO;
import com.byy.meterreading.dto.meterimage.UpdateMeterImageStatusDTO;
import com.byy.meterreading.service.MeterImageService;
import com.byy.meterreading.vo.common.PageVO;
import com.byy.meterreading.vo.meterimage.MeterImageDetailVO;
import com.byy.meterreading.vo.meterimage.MeterImageVersionVO;
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

/** 管理员查询和治理抄表图片的接口。 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMeterImageController {

    private final MeterImageService meterImageService;

    public AdminMeterImageController(MeterImageService meterImageService) {
        this.meterImageService = meterImageService;
    }

    @GetMapping("/meter-images")
    public Result<PageVO<MeterImageDetailVO>> listImages(
            @Valid @ModelAttribute MeterImagePageQueryDTO queryDTO
    ) {
        return Result.success(meterImageService.listAdminImages(queryDTO));
    }

    @GetMapping("/meter-images/{imageId}")
    public Result<MeterImageDetailVO> getImage(@PathVariable Long imageId) {
        return Result.success(meterImageService.getAdminImage(imageId));
    }

    @GetMapping("/meter-reading-tasks/{taskId}/images")
    public Result<List<MeterImageDetailVO>> listTaskImages(
            @PathVariable Long taskId
    ) {
        return Result.success(meterImageService.listAdminTaskImages(taskId));
    }

    /** 将模糊、错误或异常图片标记为无效，保留审计信息。 */
    @PutMapping("/meter-images/{imageId}/invalidate")
    public Result<MeterImageVersionVO> invalidateImage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long imageId,
            @Valid @RequestBody UpdateMeterImageStatusDTO statusDTO
    ) {
        return Result.success(meterImageService.invalidateImage(
                extractUserId(jwt), imageId, statusDTO
        ));
    }

    /** 恢复被误标为无效且 OSS 对象仍存在的图片。 */
    @PutMapping("/meter-images/{imageId}/restore")
    public Result<MeterImageVersionVO> restoreImage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long imageId,
            @Valid @RequestBody UpdateMeterImageStatusDTO statusDTO
    ) {
        return Result.success(meterImageService.restoreImage(
                extractUserId(jwt), imageId, statusDTO
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
