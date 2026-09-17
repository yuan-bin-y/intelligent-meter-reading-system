package com.byy.meterreading.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.byy.meterreading.common.exception.ResourceConflictException;
import com.byy.meterreading.dto.meter.CreateMeterDTO;
import com.byy.meterreading.mapper.MeterMapper;
import com.byy.meterreading.model.Meter;
import com.byy.meterreading.model.enums.MeterStatus;
import com.byy.meterreading.model.enums.MeterType;
import com.byy.meterreading.service.MeterService;
import com.byy.meterreading.vo.meter.CreateMeterVO;
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

    public MeterServiceImpl(MeterMapper meterMapper) {
        this.meterMapper = meterMapper;
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

        // 8. 新增接口只返回数据库主键和表具编号。
        return new CreateMeterVO(meter.getId(), meter.getMeterNo());
    }

    private boolean existsByMeterNo(String meterNo) {
        return meterMapper.selectCount(
                Wrappers.<Meter>lambdaQuery()
                        .eq(Meter::getMeterNo, meterNo)
        ) > 0;
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
