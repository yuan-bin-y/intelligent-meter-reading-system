package com.byy.meterreading.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byy.meterreading.common.exception.ObjectStorageException;
import com.byy.meterreading.common.exception.ResourceConflictException;
import com.byy.meterreading.common.exception.ResourceNotFoundException;
import com.byy.meterreading.common.exception.VersionConflictException;
import com.byy.meterreading.config.OssProperties;
import com.byy.meterreading.dto.meterimage.MeterImagePageQueryDTO;
import com.byy.meterreading.dto.meterimage.UpdateMeterImageStatusDTO;
import com.byy.meterreading.mapper.MeterImageMapper;
import com.byy.meterreading.mapper.MeterReadingTaskMapper;
import com.byy.meterreading.mapper.ResidentMeterMapper;
import com.byy.meterreading.mapper.projection.MeterImageRow;
import com.byy.meterreading.model.MeterImage;
import com.byy.meterreading.model.MeterReadingTask;
import com.byy.meterreading.model.ResidentMeter;
import com.byy.meterreading.model.enums.MeterImageStatus;
import com.byy.meterreading.model.enums.MeterImageStorageStatus;
import com.byy.meterreading.model.enums.MeterImageType;
import com.byy.meterreading.model.enums.MeterReadingTaskStatus;
import com.byy.meterreading.model.enums.TaskExecutorType;
import com.byy.meterreading.service.MeterImageService;
import com.byy.meterreading.service.MeterImageUploadCommand;
import com.byy.meterreading.service.ObjectStorageService;
import com.byy.meterreading.vo.common.PageVO;
import com.byy.meterreading.vo.meterimage.MeterImageAccessUrlVO;
import com.byy.meterreading.vo.meterimage.MeterImageDetailVO;
import com.byy.meterreading.vo.meterimage.MeterImageItemVO;
import com.byy.meterreading.vo.meterimage.MeterImageVersionVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** 阿里云 OSS 抄表图片业务实现。 */
@Service
public class MeterImageServiceImpl implements MeterImageService {

    private static final Logger log =
            LoggerFactory.getLogger(MeterImageServiceImpl.class);
    private static final int CLEANUP_BATCH_SIZE = 100;

    private final MeterImageMapper meterImageMapper;
    private final MeterReadingTaskMapper taskMapper;
    private final ResidentMeterMapper residentMeterMapper;
    private final ObjectStorageService objectStorageService;
    private final MeterImageFileInspector fileInspector;
    private final OssProperties properties;

    public MeterImageServiceImpl(
            MeterImageMapper meterImageMapper,
            MeterReadingTaskMapper taskMapper,
            ResidentMeterMapper residentMeterMapper,
            ObjectStorageService objectStorageService,
            MeterImageFileInspector fileInspector,
            OssProperties properties
    ) {
        this.meterImageMapper = meterImageMapper;
        this.taskMapper = taskMapper;
        this.residentMeterMapper = residentMeterMapper;
        this.objectStorageService = objectStorageService;
        this.fileInspector = fileInspector;
        this.properties = properties;
    }

    @Override
    public MeterImageItemVO uploadReaderImage(
            Long readerId,
            Long taskId,
            MeterImageUploadCommand command
    ) {
        return uploadImage(
                TaskExecutorType.METER_READER,
                readerId,
                taskId,
                command
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<MeterImageItemVO> listReaderTaskImages(
            Long readerId,
            Long taskId
    ) {
        requireOwnedProcessingOrLaterTask(
                TaskExecutorType.METER_READER,
                readerId,
                taskId
        );
        return meterImageMapper.selectTaskImages(taskId, false).stream()
                .filter(row -> TaskExecutorType.METER_READER.name()
                        .equals(row.getUploaderType()))
                .filter(row -> readerId.equals(row.getUploaderId()))
                .map(this::toItemVO)
                .toList();
    }

    @Override
    public void deleteReaderImage(
            Long readerId,
            Long taskId,
            Long imageId,
            Integer version
    ) {
        requirePositiveId(readerId, "抄表员ID不合法");
        requirePositiveId(taskId, "任务ID必须大于0");
        requirePositiveId(imageId, "图片ID必须大于0");
        requireVersion(version);

        MeterImageRow current = requireImageDetail(imageId);
        validateReaderImageOwnership(current, readerId, taskId);
        if (current.getResultId() != null) {
            throw new ResourceConflictException(
                    "图片已经绑定抄表结果，不能删除"
            );
        }
        if (!version.equals(current.getVersion())) {
            throw new VersionConflictException("图片已被其他操作修改");
        }

        int updatedRows = meterImageMapper.softDeleteReaderImage(
                imageId,
                taskId,
                readerId,
                version,
                LocalDateTime.now()
        );
        if (updatedRows != 1) {
            throw new ResourceConflictException(
                    "图片已绑定结果、任务状态已变化或图片已经删除"
            );
        }

        // MySQL 已经记录 DELETE_PENDING；OSS 删除失败由定时任务继续补偿。
        tryDeleteAndMark(current.getBucketName(), current.getObjectKey(), imageId);
    }

    @Override
    public MeterImageItemVO uploadDeviceImage(
            Long deviceId,
            Long taskId,
            MeterImageUploadCommand command
    ) {
        return uploadImage(
                TaskExecutorType.DEVICE,
                deviceId,
                taskId,
                command
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageVO<MeterImageDetailVO> listAdminImages(
            MeterImagePageQueryDTO queryDTO
    ) {
        validateCreatedAtRange(queryDTO);
        Page<MeterImageRow> page = new Page<>(
                queryDTO.page(),
                queryDTO.pageSize()
        );
        IPage<MeterImageRow> result = meterImageMapper.selectImagePage(
                page,
                queryDTO.taskNo(),
                queryDTO.meterNo(),
                enumName(queryDTO.uploaderType()),
                queryDTO.uploaderId(),
                enumName(queryDTO.imageStatus()),
                enumName(queryDTO.storageStatus()),
                queryDTO.bound(),
                queryDTO.deleted(),
                queryDTO.createdAtStart(),
                queryDTO.createdAtEnd()
        );
        return new PageVO<>(
                result.getRecords().stream()
                        .map(this::toDetailVO)
                        .toList(),
                result.getTotal(),
                result.getCurrent(),
                result.getSize()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public MeterImageDetailVO getAdminImage(Long imageId) {
        return toDetailVO(requireImageDetail(imageId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MeterImageDetailVO> listAdminTaskImages(Long taskId) {
        requireTask(taskId);
        return meterImageMapper.selectTaskImages(taskId, true).stream()
                .map(this::toDetailVO)
                .toList();
    }

    @Override
    @Transactional
    public MeterImageVersionVO invalidateImage(
            Long operatorId,
            Long imageId,
            UpdateMeterImageStatusDTO statusDTO
    ) {
        return updateImageStatus(
                operatorId,
                imageId,
                statusDTO,
                MeterImageStatus.VALID,
                MeterImageStatus.INVALID
        );
    }

    @Override
    @Transactional
    public MeterImageVersionVO restoreImage(
            Long operatorId,
            Long imageId,
            UpdateMeterImageStatusDTO statusDTO
    ) {
        MeterImage image = requireImage(imageId);
        if (!MeterImageStorageStatus.STORED.name()
                .equals(image.getStorageStatus())) {
            throw new IllegalArgumentException(
                    "OSS 对象不存在或等待删除，不能恢复图片"
            );
        }
        return updateImageStatus(
                operatorId,
                imageId,
                statusDTO,
                MeterImageStatus.INVALID,
                MeterImageStatus.VALID
        );
    }

    @Override
    @Transactional(readOnly = true)
    public MeterImageAccessUrlVO getAccessUrl(
            Long currentUserId,
            Set<String> roles,
            Long imageId
    ) {
        requirePositiveId(currentUserId, "当前用户ID不合法");
        MeterImageRow row = requireImageDetail(imageId);
        if (Integer.valueOf(1).equals(row.getDeleted())
                || !MeterImageStorageStatus.STORED.name()
                .equals(row.getStorageStatus())) {
            throw new ResourceNotFoundException("图片不存在");
        }

        boolean privileged = roles.contains("ADMIN")
                || roles.contains("AUDITOR");
        if (MeterImageStatus.INVALID.name().equals(row.getImageStatus())
                && !privileged) {
            throw new ResourceNotFoundException("图片不存在");
        }
        if (!privileged && !canUserAccess(row, currentUserId, roles)) {
            throw new ResourceNotFoundException("图片不存在或无权访问");
        }

        String accessUrl = objectStorageService.generateAccessUrl(
                row.getBucketName(),
                row.getObjectKey()
        );
        return new MeterImageAccessUrlVO(
                imageId,
                accessUrl,
                properties.getAccessUrlTtl().toSeconds()
        );
    }

    /** 定时补偿 DELETE_PENDING，并清理超过保留期的无效或孤立图片。 */
    @Override
    public void cleanupExpiredImages() {
        if (!objectStorageService.isEnabled()) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now()
                .minus(properties.getCleanupRetention());
        List<MeterImage> candidates =
                meterImageMapper.selectCleanupCandidates(
                        cutoff,
                        CLEANUP_BATCH_SIZE
                );
        for (MeterImage image : candidates) {
            if (MeterImageStorageStatus.STORED.name()
                    .equals(image.getStorageStatus())) {
                int marked = meterImageMapper.update(
                        null,
                        Wrappers.<MeterImage>lambdaUpdate()
                                .set(MeterImage::getStorageStatus,
                                        MeterImageStorageStatus.DELETE_PENDING
                                                .name())
                                .setSql("version = version + 1")
                                .eq(MeterImage::getId, image.getId())
                                .eq(MeterImage::getStorageStatus,
                                        MeterImageStorageStatus.STORED.name())
                                .eq(MeterImage::getVersion,
                                        image.getVersion())
                );
                if (marked != 1) {
                    continue;
                }
            }
            tryDeleteAndMark(
                    image.getBucketName(),
                    image.getObjectKey(),
                    image.getId()
            );
        }
    }

    private MeterImageItemVO uploadImage(
            TaskExecutorType uploaderType,
            Long uploaderId,
            Long taskId,
            MeterImageUploadCommand command
    ) {
        validateUploadCommand(command);
        requireOwnedProcessingTask(uploaderType, uploaderId, taskId);
        MeterImageFileInspector.InspectedImage inspected =
                fileInspector.inspect(
                        command.content(),
                        command.declaredContentType()
                );

        MeterImage existing = findByUploadRequest(
                uploaderType,
                uploaderId,
                command.requestId()
        );
        if (existing != null) {
            return resolveIdempotentUpload(
                    existing,
                    taskId,
                    command,
                    inspected
            );
        }
        requireImageCapacity(taskId, uploaderType, uploaderId);

        String objectKey = generateObjectKey(
                taskId,
                inspected.extension()
        );
        ObjectStorageService.StoredObject stored =
                objectStorageService.upload(
                        objectKey,
                        command.content(),
                        inspected.contentType()
                );

        MeterImage image = MeterImage.builder()
                .taskId(taskId)
                .uploaderType(uploaderType.name())
                .uploaderId(uploaderId)
                .uploadRequestId(command.requestId().trim())
                .imageType(command.imageType().name())
                .originalName(normalizeOriginalName(command.originalName()))
                .bucketName(stored.bucketName())
                .objectKey(stored.objectKey())
                .ossEtag(stored.eTag())
                .contentType(inspected.contentType())
                .fileSize(inspected.fileSize())
                .imageWidth(inspected.width())
                .imageHeight(inspected.height())
                .sha256(inspected.sha256())
                .imageStatus(MeterImageStatus.VALID.name())
                .storageStatus(MeterImageStorageStatus.STORED.name())
                .deleted(0)
                .version(0)
                .build();
        try {
            // OSS 上传后再次校验，状态变化时删除刚上传的对象作为补偿。
            requireOwnedProcessingTask(uploaderType, uploaderId, taskId);
            requireImageCapacity(taskId, uploaderType, uploaderId);
            meterImageMapper.insert(image);
            return toItemVO(requireImageDetail(image.getId()));
        } catch (DuplicateKeyException exception) {
            compensateUploadedObject(stored);
            MeterImage concurrent = findByUploadRequest(
                    uploaderType,
                    uploaderId,
                    command.requestId()
            );
            if (concurrent != null) {
                return resolveIdempotentUpload(
                        concurrent,
                        taskId,
                        command,
                        inspected
                );
            }
            throw new ResourceConflictException("图片上传请求发生冲突", exception);
        } catch (RuntimeException exception) {
            compensateUploadedObject(stored);
            throw exception;
        }
    }

    private MeterImageItemVO resolveIdempotentUpload(
            MeterImage existing,
            Long taskId,
            MeterImageUploadCommand command,
            MeterImageFileInspector.InspectedImage inspected
    ) {
        boolean same = taskId.equals(existing.getTaskId())
                && command.imageType().name().equals(existing.getImageType())
                && inspected.sha256().equals(existing.getSha256())
                && Integer.valueOf(0).equals(existing.getDeleted());
        if (!same) {
            throw new ResourceConflictException(
                    "同一幂等标识已经用于其他图片上传请求"
            );
        }
        return toItemVO(requireImageDetail(existing.getId()));
    }

    private MeterImageVersionVO updateImageStatus(
            Long operatorId,
            Long imageId,
            UpdateMeterImageStatusDTO statusDTO,
            MeterImageStatus expectedStatus,
            MeterImageStatus targetStatus
    ) {
        requirePositiveId(operatorId, "当前管理员ID不合法");
        requirePositiveId(imageId, "图片ID必须大于0");
        MeterImage image = requireImage(imageId);
        requireVersion(statusDTO.version());
        if (!statusDTO.version().equals(image.getVersion())) {
            throw new VersionConflictException("图片已被其他操作修改");
        }
        if (Integer.valueOf(1).equals(image.getDeleted())) {
            throw new IllegalArgumentException("已删除图片不能修改状态");
        }
        if (!expectedStatus.name().equals(image.getImageStatus())) {
            throw new IllegalArgumentException(
                    "图片当前状态不允许执行该操作"
            );
        }

        int updated = meterImageMapper.update(
                null,
                Wrappers.<MeterImage>lambdaUpdate()
                        .set(MeterImage::getImageStatus, targetStatus.name())
                        .set(MeterImage::getStatusReason,
                                statusDTO.reason())
                        .set(MeterImage::getStatusChangedBy, operatorId)
                        .set(MeterImage::getStatusChangedAt,
                                LocalDateTime.now())
                        .setSql("version = version + 1")
                        .eq(MeterImage::getId, imageId)
                        .eq(MeterImage::getImageStatus,
                                expectedStatus.name())
                        .eq(MeterImage::getVersion,
                                statusDTO.version())
                        .eq(MeterImage::getDeleted, 0)
        );
        if (updated != 1) {
            throw new VersionConflictException("图片已被其他操作修改");
        }
        return new MeterImageVersionVO(
                imageId,
                targetStatus,
                statusDTO.version() + 1
        );
    }

    private boolean canUserAccess(
            MeterImageRow row,
            Long currentUserId,
            Set<String> roles
    ) {
        if (roles.contains("METER_READER")
                && TaskExecutorType.METER_READER.name()
                .equals(row.getUploaderType())
                && currentUserId.equals(row.getUploaderId())) {
            return true;
        }
        if (roles.contains("RESIDENT")
                && MeterReadingTaskStatus.COMPLETED.name()
                .equals(row.getTaskStatus())) {
            return residentMeterMapper.selectCount(
                    Wrappers.<ResidentMeter>lambdaQuery()
                            .eq(ResidentMeter::getResidentId,
                                    currentUserId)
                            .eq(ResidentMeter::getMeterId,
                                    row.getMeterId())
            ) > 0;
        }
        return false;
    }

    private void requireOwnedProcessingTask(
            TaskExecutorType uploaderType,
            Long uploaderId,
            Long taskId
    ) {
        MeterReadingTask task = requireOwnedTask(
                uploaderType,
                uploaderId,
                taskId
        );
        if (!MeterReadingTaskStatus.PROCESSING.name()
                .equals(task.getTaskStatus())) {
            throw new IllegalArgumentException(
                    "只有执行中的任务可以上传图片"
            );
        }
    }

    private void requireOwnedProcessingOrLaterTask(
            TaskExecutorType uploaderType,
            Long uploaderId,
            Long taskId
    ) {
        requireOwnedTask(uploaderType, uploaderId, taskId);
    }

    private MeterReadingTask requireOwnedTask(
            TaskExecutorType uploaderType,
            Long uploaderId,
            Long taskId
    ) {
        requirePositiveId(uploaderId, "当前执行者ID不合法");
        MeterReadingTask task = requireTask(taskId);
        Long assignedId = uploaderType == TaskExecutorType.METER_READER
                ? task.getMeterReaderId()
                : task.getDeviceId();
        if (!uploaderType.name().equals(task.getExecutorType())
                || !uploaderId.equals(assignedId)) {
            throw new ResourceNotFoundException(
                    "抄表任务不存在或不属于当前执行者"
            );
        }
        return task;
    }

    private MeterReadingTask requireTask(Long taskId) {
        requirePositiveId(taskId, "任务ID必须大于0");
        MeterReadingTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new ResourceNotFoundException("抄表任务不存在");
        }
        return task;
    }

    private MeterImage requireImage(Long imageId) {
        requirePositiveId(imageId, "图片ID必须大于0");
        MeterImage image = meterImageMapper.selectById(imageId);
        if (image == null) {
            throw new ResourceNotFoundException("图片不存在");
        }
        return image;
    }

    private MeterImageRow requireImageDetail(Long imageId) {
        requirePositiveId(imageId, "图片ID必须大于0");
        MeterImageRow row = meterImageMapper.selectImageDetail(imageId);
        if (row == null) {
            throw new ResourceNotFoundException("图片不存在");
        }
        return row;
    }

    private void validateReaderImageOwnership(
            MeterImageRow image,
            Long readerId,
            Long taskId
    ) {
        if (!taskId.equals(image.getTaskId())
                || !TaskExecutorType.METER_READER.name()
                .equals(image.getUploaderType())
                || !readerId.equals(image.getUploaderId())
                || Integer.valueOf(1).equals(image.getDeleted())) {
            throw new ResourceNotFoundException(
                    "图片不存在或不属于当前抄表员"
            );
        }
    }

    private void requireImageCapacity(
            Long taskId,
            TaskExecutorType uploaderType,
            Long uploaderId
    ) {
        long count = meterImageMapper.selectCount(
                Wrappers.<MeterImage>lambdaQuery()
                        .eq(MeterImage::getTaskId, taskId)
                        .eq(MeterImage::getUploaderType,
                                uploaderType.name())
                        .eq(MeterImage::getUploaderId, uploaderId)
                        .eq(MeterImage::getDeleted, 0)
                        .eq(MeterImage::getImageStatus,
                                MeterImageStatus.VALID.name())
                        .ne(MeterImage::getStorageStatus,
                                MeterImageStorageStatus.DELETED.name())
        );
        if (count >= properties.getMaxImagesPerTask()) {
            throw new ResourceConflictException(
                    "当前任务上传图片数量已经达到上限"
            );
        }
    }

    private MeterImage findByUploadRequest(
            TaskExecutorType uploaderType,
            Long uploaderId,
            String requestId
    ) {
        return meterImageMapper.selectOne(
                Wrappers.<MeterImage>lambdaQuery()
                        .eq(MeterImage::getUploaderType,
                                uploaderType.name())
                        .eq(MeterImage::getUploaderId, uploaderId)
                        .eq(MeterImage::getUploadRequestId,
                                requestId.trim())
                        .last("LIMIT 1")
        );
    }

    private void validateUploadCommand(MeterImageUploadCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("上传参数不能为空");
        }
        if (command.requestId() == null
                || command.requestId().isBlank()
                || command.requestId().trim().length() > 64) {
            throw new IllegalArgumentException(
                    "X-Idempotency-Key 不能为空且长度不能超过64个字符"
            );
        }
        if (command.imageType() == null) {
            throw new IllegalArgumentException("图片类型不能为空");
        }
    }

    private String normalizeOriginalName(String originalName) {
        String value = originalName == null || originalName.isBlank()
                ? "image"
                : originalName.replace('\\', '/');
        int slash = value.lastIndexOf('/');
        if (slash >= 0) {
            value = value.substring(slash + 1);
        }
        value = value.trim();
        if (value.isEmpty()) {
            value = "image";
        }
        return value.length() <= 255 ? value : value.substring(0, 255);
    }

    private String generateObjectKey(Long taskId, String extension) {
        LocalDate today = LocalDate.now();
        return "meter-images/%d/%02d/%d/%s.%s".formatted(
                today.getYear(),
                today.getMonthValue(),
                taskId,
                UUID.randomUUID().toString().replace("-", ""),
                extension.toLowerCase(Locale.ROOT)
        );
    }

    private void compensateUploadedObject(
            ObjectStorageService.StoredObject stored
    ) {
        try {
            objectStorageService.delete(
                    stored.bucketName(),
                    stored.objectKey()
            );
        } catch (ObjectStorageException exception) {
            log.error(
                    "数据库保存失败且 OSS 补偿删除失败，bucket={}, objectKey={}",
                    stored.bucketName(),
                    stored.objectKey(),
                    exception
            );
        }
    }

    private void tryDeleteAndMark(
            String bucketName,
            String objectKey,
            Long imageId
    ) {
        try {
            objectStorageService.delete(bucketName, objectKey);
            meterImageMapper.update(
                    null,
                    Wrappers.<MeterImage>lambdaUpdate()
                            .set(MeterImage::getStorageStatus,
                                    MeterImageStorageStatus.DELETED.name())
                            .setSql("version = version + 1")
                            .eq(MeterImage::getId, imageId)
                            .eq(MeterImage::getStorageStatus,
                                    MeterImageStorageStatus.DELETE_PENDING
                                            .name())
            );
        } catch (ObjectStorageException exception) {
            log.warn(
                    "OSS 图片删除失败，等待定时补偿，imageId={}",
                    imageId,
                    exception
            );
        }
    }

    private void validateCreatedAtRange(MeterImagePageQueryDTO queryDTO) {
        if (queryDTO.createdAtStart() != null
                && queryDTO.createdAtEnd() != null
                && queryDTO.createdAtEnd()
                .isBefore(queryDTO.createdAtStart())) {
            throw new IllegalArgumentException(
                    "上传结束时间不能早于开始时间"
            );
        }
    }

    private void requirePositiveId(Long value, String message) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private void requireVersion(Integer version) {
        if (version == null || version < 0) {
            throw new IllegalArgumentException(
                    "数据版本不能为空且不能小于0"
            );
        }
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private MeterImageItemVO toItemVO(MeterImageRow row) {
        MeterImageType imageType = MeterImageType.valueOf(
                row.getImageType()
        );
        MeterImageStatus imageStatus = MeterImageStatus.valueOf(
                row.getImageStatus()
        );
        return new MeterImageItemVO(
                row.getImageId(),
                row.getTaskId(),
                imageType,
                imageType.getDescription(),
                row.getOriginalName(),
                row.getContentType(),
                row.getFileSize(),
                row.getImageWidth(),
                row.getImageHeight(),
                imageStatus,
                imageStatus.getDescription(),
                MeterImageStorageStatus.valueOf(row.getStorageStatus()),
                row.getResultId(),
                row.getVersion(),
                row.getCreatedAt()
        );
    }

    private MeterImageDetailVO toDetailVO(MeterImageRow row) {
        TaskExecutorType uploaderType = TaskExecutorType.valueOf(
                row.getUploaderType()
        );
        MeterImageType imageType = MeterImageType.valueOf(
                row.getImageType()
        );
        MeterImageStatus imageStatus = MeterImageStatus.valueOf(
                row.getImageStatus()
        );
        MeterImageStorageStatus storageStatus =
                MeterImageStorageStatus.valueOf(row.getStorageStatus());
        return new MeterImageDetailVO(
                row.getImageId(),
                row.getTaskId(),
                row.getTaskNo(),
                row.getTaskStatus(),
                row.getMeterId(),
                row.getMeterNo(),
                row.getMeterName(),
                uploaderType,
                uploaderType.getDescription(),
                row.getUploaderId(),
                row.getUploaderCode(),
                row.getUploaderName(),
                imageType,
                imageType.getDescription(),
                row.getOriginalName(),
                row.getBucketName(),
                row.getObjectKey(),
                row.getOssEtag(),
                row.getContentType(),
                row.getFileSize(),
                row.getImageWidth(),
                row.getImageHeight(),
                row.getSha256(),
                imageStatus,
                imageStatus.getDescription(),
                storageStatus,
                storageStatus.getDescription(),
                row.getStatusReason(),
                row.getStatusChangedBy(),
                row.getStatusChangedAt(),
                Integer.valueOf(1).equals(row.getDeleted()),
                row.getDeletedBy(),
                row.getDeletedAt(),
                row.getResultId(),
                row.getVersion(),
                row.getCreatedAt(),
                row.getUpdatedAt()
        );
    }
}
