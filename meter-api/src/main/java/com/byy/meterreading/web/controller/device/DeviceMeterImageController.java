package com.byy.meterreading.web.controller.device;

import com.byy.meterreading.auth.device.DevicePrincipal;
import com.byy.meterreading.common.result.Result;
import com.byy.meterreading.model.enums.MeterImageType;
import com.byy.meterreading.service.MeterImageService;
import com.byy.meterreading.service.MeterImageUploadCommand;
import com.byy.meterreading.vo.meterimage.MeterImageItemVO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** 采集设备给分配到本机的执行中任务上传图片。 */
@RestController
@RequestMapping("/api/v1/device/meter-reading-tasks/{taskId}/images")
@PreAuthorize("hasRole('DEVICE')")
public class DeviceMeterImageController {

    private final MeterImageService meterImageService;

    public DeviceMeterImageController(MeterImageService meterImageService) {
        this.meterImageService = meterImageService;
    }

    @PostMapping(consumes = "multipart/form-data")
    public Result<MeterImageItemVO> uploadImage(
            @AuthenticationPrincipal DevicePrincipal principal,
            @PathVariable Long taskId,
            @RequestHeader("X-Idempotency-Key") String requestId,
            @RequestParam(defaultValue = "ORIGINAL") MeterImageType imageType,
            @RequestPart("file") MultipartFile file
    ) {
        return Result.success(meterImageService.uploadDeviceImage(
                principal.deviceId(),
                taskId,
                toUploadCommand(requestId, imageType, file)
        ));
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
}
