package com.byy.meterreading.web.controller.meterreader;

import com.byy.meterreading.common.result.Result;
import com.byy.meterreading.model.enums.MeterImageType;
import com.byy.meterreading.service.MeterImageService;
import com.byy.meterreading.service.MeterImageUploadCommand;
import com.byy.meterreading.vo.meterimage.MeterImageItemVO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/** 抄表员上传、查询和删除本人任务图片的接口。 */
@RestController
@RequestMapping("/api/v1/meter-reader/tasks/{taskId}/images")
@PreAuthorize("hasRole('METER_READER')")
public class MeterReaderImageController {

    private final MeterImageService meterImageService;

    public MeterReaderImageController(MeterImageService meterImageService) {
        this.meterImageService = meterImageService;
    }

    /**
     * 上传图片前，任务必须属于当前抄表员并处于 PROCESSING 状态。
     * X-Idempotency-Key 用来防止客户端重试产生重复 OSS 对象和数据库记录。
     */
    @PostMapping(consumes = "multipart/form-data")
    public Result<MeterImageItemVO> uploadImage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long taskId,
            @RequestHeader("X-Idempotency-Key") String requestId,
            @RequestParam(defaultValue = "ORIGINAL") MeterImageType imageType,
            @RequestPart("file") MultipartFile file
    ) {
        return Result.success(meterImageService.uploadReaderImage(
                extractUserId(jwt),
                taskId,
                toUploadCommand(requestId, imageType, file)
        ));
    }

    /** 查询当前抄表员在指定任务中上传且尚未删除的图片。 */
    @GetMapping
    public Result<List<MeterImageItemVO>> listImages(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long taskId
    ) {
        return Result.success(meterImageService.listReaderTaskImages(
                extractUserId(jwt),
                taskId
        ));
    }

    /**
     * 软删除误传图片；只有 PROCESSING 任务中尚未绑定结果的本人图片允许删除。
     */
    @DeleteMapping("/{imageId}")
    public Result<Void> deleteImage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long taskId,
            @PathVariable Long imageId,
            @RequestParam Integer version
    ) {
        meterImageService.deleteReaderImage(
                extractUserId(jwt),
                taskId,
                imageId,
                version
        );
        return Result.success(null);
    }

    private MeterImageUploadCommand toUploadCommand(
            String requestId,
            MeterImageType imageType,
            MultipartFile file
    ) {
        try {
            return new MeterImageUploadCommand(
                    requestId,
                    imageType,
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getBytes()
            );
        } catch (IOException exception) {
            throw new IllegalArgumentException("读取上传图片失败", exception);
        }
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
