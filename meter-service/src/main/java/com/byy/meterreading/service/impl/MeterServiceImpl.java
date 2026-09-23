package com.byy.meterreading.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byy.meterreading.cache.BusinessCacheService;
import com.byy.meterreading.common.exception.ResourceConflictException;
import com.byy.meterreading.common.exception.ResourceNotFoundException;
import com.byy.meterreading.common.exception.VersionConflictException;
import com.byy.meterreading.dto.meter.CreateMeterDTO;
import com.byy.meterreading.dto.meter.MeterPageQueryDTO;
import com.byy.meterreading.dto.meter.UpdateMeterDTO;
import com.byy.meterreading.dto.meter.UpdateMeterStatusDTO;
import com.byy.meterreading.mapper.MeterMapper;
import com.byy.meterreading.model.Meter;
import com.byy.meterreading.model.enums.MeterDisplayType;
import com.byy.meterreading.model.enums.MeterStatus;
import com.byy.meterreading.model.enums.MeterType;
import com.byy.meterreading.service.MeterService;
import com.byy.meterreading.service.ResidentMeterService;
import com.byy.meterreading.service.DeviceMeterService;
import com.byy.meterreading.vo.common.PageVO;
import com.byy.meterreading.vo.meter.CreateMeterVO;
import com.byy.meterreading.vo.meter.MeterDetailVO;
import com.byy.meterreading.vo.meter.MeterListItemVO;
import com.byy.meterreading.vo.meter.MeterVersionVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 表具管理业务实现。
 */
@Service
public class MeterServiceImpl implements MeterService {

    private static final Map<MeterType, String> METER_UNITS = Map.of(
            MeterType.WATER, "m³",
            MeterType.ELECTRIC, "kWh",
            MeterType.GAS, "m³"
    );

    private final MeterMapper meterMapper;
    private final ResidentMeterService residentMeterService;
    private final DeviceMeterService deviceMeterService;
    private final BusinessCacheService businessCacheService;

    public MeterServiceImpl(
            MeterMapper meterMapper,
            ResidentMeterService residentMeterService,
            DeviceMeterService deviceMeterService,
            BusinessCacheService businessCacheService
    ) {
        this.meterMapper = meterMapper;
        this.residentMeterService = residentMeterService;
        this.deviceMeterService = deviceMeterService;
        this.businessCacheService = businessCacheService;
    }

    /**
     * 新增表具，并通过预查询和数据库唯一索引共同保证表具编号唯一。
     */
    @Override
    public CreateMeterVO createMeter(
            Long operatorId,
            CreateMeterDTO createMeterDTO
    ) {
        // 1. 操作人必须来自已经通过认证的 JWT。
        if (operatorId == null) {
            throw new IllegalArgumentException("当前操作人不能为空");
        }

        // 2. 统一去除文本首尾空格，避免保存无意义的空白差异。
        String meterNo = createMeterDTO.meterNo().trim();
        String meterName = createMeterDTO.meterName().trim();
        String unit = createMeterDTO.unit().trim();
        String remark = normalizeOptionalText(createMeterDTO.remark());

        // 3. 固定表具类型只能使用对应的标准计量单位。
        validateUnit(createMeterDTO.meterType(), unit);

        // 4. 初始读数必须能够被当前表盘的整数位和小数位完整显示。
        validateReadingDigits(
                createMeterDTO.initialReading(),
                createMeterDTO.integerDigits(),
                createMeterDTO.decimalDigits()
        );

        // 5. 正常情况下先返回明确提示；并发插入仍由唯一索引最终兜底。
        if (existsByMeterNo(meterNo)) {
            throw new ResourceConflictException("表具编号已存在");
        }

        // 6. 系统字段全部由后端生成，前端不能指定状态、版本和操作人。
        Meter meter = Meter.builder()
                .meterNo(meterNo)
                .meterName(meterName)
                .meterType(createMeterDTO.meterType().name())
                .displayType(createMeterDTO.displayType().name())
                .unit(unit)
                .integerDigits(createMeterDTO.integerDigits())
                .decimalDigits(createMeterDTO.decimalDigits())
                .initialReading(createMeterDTO.initialReading())
                .installedAt(createMeterDTO.installedAt())
                .status(MeterStatus.ACTIVE.getCode())
                .version(0)
                .remark(remark)
                .createdBy(operatorId)
                .updatedBy(operatorId)
                .deleted(0)
                .build();

        try {
            // 7. MyBatis-Plus 插入后会把 MySQL 自增主键回填到实体中。
            meterMapper.insert(meter);
        } catch (DuplicateKeyException exception) {
            // 两个相同表号的请求可能同时通过预查询，最终由唯一索引兜底。
            throw new ResourceConflictException("表具编号已存在", exception);
        }

        // 清除该主键可能存在的短期空值缓存。
        businessCacheService.evictMeterDetailAfterCommit(meter.getId());

        // 8. 新增接口只返回数据库主键和表具编号。
        return new CreateMeterVO(meter.getId(), meter.getMeterNo());
    }

    /**
     * 使用 MyBatis-Plus 动态条件和分页插件查询未删除表具。
     */
    @Override
    public PageVO<MeterListItemVO> listMeters(
            MeterPageQueryDTO queryDTO
    ) {
        LambdaQueryWrapper<Meter> queryWrapper =
                Wrappers.<Meter>lambdaQuery()
                        .and(queryDTO.keyword() != null, wrapper -> wrapper
                                .like(Meter::getMeterNo, queryDTO.keyword())
                                .or()
                                .like(Meter::getMeterName, queryDTO.keyword())
                        )
                        .eq(queryDTO.meterType() != null,
                                Meter::getMeterType,
                                queryDTO.meterType() == null
                                        ? null
                                        : queryDTO.meterType().name())
                        .eq(queryDTO.displayType() != null,
                                Meter::getDisplayType,
                                queryDTO.displayType() == null
                                        ? null
                                        : queryDTO.displayType().name())
                        .eq(queryDTO.status() != null,
                                Meter::getStatus,
                                queryDTO.status())
                        .orderByDesc(Meter::getCreatedAt)
                        .orderByDesc(Meter::getId);

        Page<Meter> page = new Page<>(
                queryDTO.page(),
                queryDTO.pageSize()
        );
        IPage<Meter> meterPage = meterMapper.selectPage(
                page,
                queryWrapper
        );

        return new PageVO<>(
                meterPage.getRecords().stream()
                        .map(this::toListItemVO)
                        .toList(),
                meterPage.getTotal(),
                meterPage.getCurrent(),
                meterPage.getSize()
        );
    }

    @Override
    public MeterDetailVO getMeter(Long meterId) {
        requirePositiveMeterId(meterId);
        MeterDetailVO detail = businessCacheService.getMeterDetail(
                meterId,
                () -> {
                    Meter meter = meterMapper.selectById(meterId);
                    return meter == null ? null : toDetailVO(meter);
                }
        );
        if (detail == null) {
            throw new ResourceNotFoundException("表具不存在");
        }
        return detail;
    }

    /**
     * 修改表具基础资料，使用版本号条件保证并发更新不会互相覆盖。
     */
    @Override
    public MeterVersionVO updateMeter(
            Long operatorId,
            Long meterId,
            UpdateMeterDTO updateMeterDTO
    ) {
        requireOperatorId(operatorId);
        Meter currentMeter = requireMeter(meterId);
        requireExpectedVersion(currentMeter, updateMeterDTO.version());

        MeterStatus currentStatus = MeterStatus.fromCode(
                currentMeter.getStatus()
        );
        if (currentStatus == MeterStatus.SCRAPPED) {
            throw new IllegalArgumentException("已报废表具不能修改资料");
        }

        String meterName = updateMeterDTO.meterName().trim();
        String unit = updateMeterDTO.unit().trim();
        String remark = normalizeOptionalText(updateMeterDTO.remark());
        validateUnit(updateMeterDTO.meterType(), unit);
        validateReadingDigits(
                updateMeterDTO.initialReading(),
                updateMeterDTO.integerDigits(),
                updateMeterDTO.decimalDigits()
        );

        LambdaUpdateWrapper<Meter> updateWrapper =
                Wrappers.<Meter>lambdaUpdate()
                        .set(Meter::getMeterName, meterName)
                        .set(Meter::getMeterType,
                                updateMeterDTO.meterType().name())
                        .set(Meter::getDisplayType,
                                updateMeterDTO.displayType().name())
                        .set(Meter::getUnit, unit)
                        .set(Meter::getIntegerDigits,
                                updateMeterDTO.integerDigits())
                        .set(Meter::getDecimalDigits,
                                updateMeterDTO.decimalDigits())
                        .set(Meter::getInitialReading,
                                updateMeterDTO.initialReading())
                        .set(Meter::getInstalledAt,
                                updateMeterDTO.installedAt())
                        .set(Meter::getRemark, remark)
                        .set(Meter::getUpdatedBy, operatorId)
                        .setSql("version = version + 1")
                        .eq(Meter::getId, meterId)
                        .eq(Meter::getVersion, updateMeterDTO.version());

        int updatedRows = meterMapper.update(null, updateWrapper);
        ensureUpdated(updatedRows, meterId);
        businessCacheService.evictMeterDetailAfterCommit(meterId);
        return new MeterVersionVO(
                meterId,
                updateMeterDTO.version() + 1
        );
    }

    /**
     * 校验生命周期流转后，按版本号原子更新表具状态。
     */
    @Override
    public MeterVersionVO updateMeterStatus(
            Long operatorId,
            Long meterId,
            UpdateMeterStatusDTO updateMeterStatusDTO
    ) {
        requireOperatorId(operatorId);
        Meter currentMeter = requireMeter(meterId);
        requireExpectedVersion(
                currentMeter,
                updateMeterStatusDTO.version()
        );

        MeterStatus currentStatus = MeterStatus.fromCode(
                currentMeter.getStatus()
        );
        MeterStatus targetStatus = MeterStatus.fromCode(
                updateMeterStatusDTO.status()
        );
        if (!currentStatus.canTransitionTo(targetStatus)) {
            throw new IllegalArgumentException(
                    "不允许将表具状态从"
                            + currentStatus.getDescription()
                            + "修改为"
                            + targetStatus.getDescription()
            );
        }

        LambdaUpdateWrapper<Meter> updateWrapper =
                Wrappers.<Meter>lambdaUpdate()
                        .set(Meter::getStatus, targetStatus.getCode())
                        .set(Meter::getUpdatedBy, operatorId)
                        .setSql("version = version + 1")
                        .eq(Meter::getId, meterId)
                        .eq(Meter::getVersion,
                                updateMeterStatusDTO.version());

        int updatedRows = meterMapper.update(null, updateWrapper);
        ensureUpdated(updatedRows, meterId);
        businessCacheService.evictMeterDetailAfterCommit(meterId);
        return new MeterVersionVO(
                meterId,
                updateMeterStatusDTO.version() + 1
        );
    }

    /**
     * 表具必须停用，并解除居民和设备绑定后才能逻辑删除。
     */
    @Override
    public void deleteMeter(
            Long operatorId,
            Long meterId,
            Integer version
    ) {
        requireOperatorId(operatorId);
        if (version == null || version < 0) {
            throw new IllegalArgumentException("数据版本不能为空且不能小于0");
        }

        Meter currentMeter = requireMeter(meterId);
        requireExpectedVersion(currentMeter, version);
        if (MeterStatus.fromCode(currentMeter.getStatus())
                != MeterStatus.DISABLED) {
            throw new IllegalArgumentException("只有停用状态的表具可以删除");
        }
        if (residentMeterService.hasBindingsByMeterId(meterId)) {
            throw new ResourceConflictException(
                    "表具仍绑定居民，不能删除"
            );
        }
        if (deviceMeterService.hasBindingsByMeterId(meterId)) {
            throw new ResourceConflictException(
                    "表具仍绑定设备，不能删除"
            );
        }

        LambdaUpdateWrapper<Meter> updateWrapper =
                Wrappers.<Meter>lambdaUpdate()
                        .set(Meter::getDeleted, 1)
                        .set(Meter::getUpdatedBy, operatorId)
                        .setSql("version = version + 1")
                        .eq(Meter::getId, meterId)
                        .eq(Meter::getVersion, version);

        int updatedRows = meterMapper.update(null, updateWrapper);
        ensureUpdated(updatedRows, meterId);
        businessCacheService.evictMeterDetailAfterCommit(meterId);
    }

    private boolean existsByMeterNo(String meterNo) {
        return meterMapper.selectCount(
                Wrappers.<Meter>lambdaQuery()
                        .eq(Meter::getMeterNo, meterNo)
        ) > 0;
    }

    private Meter requireMeter(Long meterId) {
        requirePositiveMeterId(meterId);
        Meter meter = meterMapper.selectById(meterId);
        if (meter == null) {
            throw new ResourceNotFoundException("表具不存在");
        }
        return meter;
    }

    private void requirePositiveMeterId(Long meterId) {
        if (meterId == null || meterId <= 0) {
            throw new IllegalArgumentException("表具ID必须大于0");
        }
    }

    private void requireOperatorId(Long operatorId) {
        if (operatorId == null) {
            throw new IllegalArgumentException("当前操作人不能为空");
        }
    }

    private void requireExpectedVersion(
            Meter meter,
            Integer expectedVersion
    ) {
        if (expectedVersion == null || expectedVersion < 0) {
            throw new IllegalArgumentException("数据版本不能为空且不能小于0");
        }
        if (!expectedVersion.equals(meter.getVersion())) {
            throw new VersionConflictException(
                    "表具信息已被其他用户修改，请刷新后重试"
            );
        }
    }

    private void ensureUpdated(int updatedRows, Long meterId) {
        if (updatedRows == 1) {
            return;
        }
        Meter latestMeter = meterMapper.selectById(meterId);
        if (latestMeter == null) {
            throw new ResourceNotFoundException("表具不存在");
        }
        throw new VersionConflictException(
                "表具信息已被其他用户修改，请刷新后重试"
        );
    }

    private MeterListItemVO toListItemVO(Meter meter) {
        return new MeterListItemVO(
                meter.getId(),
                meter.getMeterNo(),
                meter.getMeterName(),
                MeterType.valueOf(meter.getMeterType()),
                MeterDisplayType.valueOf(meter.getDisplayType()),
                meter.getUnit(),
                meter.getStatus(),
                meter.getVersion(),
                meter.getUpdatedAt()
        );
    }

    private MeterDetailVO toDetailVO(Meter meter) {
        return new MeterDetailVO(
                meter.getId(),
                meter.getMeterNo(),
                meter.getMeterName(),
                MeterType.valueOf(meter.getMeterType()),
                MeterDisplayType.valueOf(meter.getDisplayType()),
                meter.getUnit(),
                meter.getIntegerDigits(),
                meter.getDecimalDigits(),
                meter.getInitialReading(),
                meter.getInstalledAt(),
                meter.getStatus(),
                meter.getVersion(),
                meter.getRemark(),
                meter.getCreatedBy(),
                meter.getUpdatedBy(),
                meter.getCreatedAt(),
                meter.getUpdatedAt()
        );
    }

    private void validateUnit(MeterType meterType, String unit) {
        String expectedUnit = METER_UNITS.get(meterType);
        if (!unit.equals(expectedUnit)) {
            throw new IllegalArgumentException(
                    meterType.getDescription()
                            + "的计量单位必须是"
                            + expectedUnit
            );
        }
    }

    private void validateReadingDigits(
            BigDecimal reading,
            Integer integerDigits,
            Integer decimalDigits
    ) {
        if (reading.signum() < 0) {
            throw new IllegalArgumentException("初始读数不能小于0");
        }

        BigDecimal normalizedReading = reading.stripTrailingZeros();
        int actualDecimalDigits = Math.max(normalizedReading.scale(), 0);
        if (actualDecimalDigits > decimalDigits) {
            throw new IllegalArgumentException(
                    "初始读数的小数位数不能超过" + decimalDigits + "位"
            );
        }

        BigDecimal maximumExclusive = BigDecimal.TEN.pow(integerDigits);
        if (reading.compareTo(maximumExclusive) >= 0) {
            throw new IllegalArgumentException(
                    "初始读数的整数位数不能超过" + integerDigits + "位"
            );
        }
    }

    private String normalizeOptionalText(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
