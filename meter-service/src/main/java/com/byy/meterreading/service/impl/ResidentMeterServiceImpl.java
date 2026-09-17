package com.byy.meterreading.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byy.meterreading.common.exception.ResourceConflictException;
import com.byy.meterreading.common.exception.ResourceNotFoundException;
import com.byy.meterreading.dto.residentmeter.BindResidentMeterDTO;
import com.byy.meterreading.dto.residentmeter.BindingResourcePageQueryDTO;
import com.byy.meterreading.dto.residentmeter.ResidentMeterPageQueryDTO;
import com.byy.meterreading.mapper.MeterMapper;
import com.byy.meterreading.mapper.ResidentMeterMapper;
import com.byy.meterreading.mapper.projection.MeterBoundResidentRow;
import com.byy.meterreading.mapper.projection.ResidentBoundMeterRow;
import com.byy.meterreading.mapper.projection.ResidentMeterBindingRow;
import com.byy.meterreading.model.Meter;
import com.byy.meterreading.model.ResidentMeter;
import com.byy.meterreading.model.SysUser;
import com.byy.meterreading.model.enums.MeterDisplayType;
import com.byy.meterreading.model.enums.MeterStatus;
import com.byy.meterreading.model.enums.MeterType;
import com.byy.meterreading.service.ResidentMeterService;
import com.byy.meterreading.service.SysUserService;
import com.byy.meterreading.vo.common.PageVO;
import com.byy.meterreading.vo.residentmeter.MeterBoundResidentVO;
import com.byy.meterreading.vo.residentmeter.ResidentBoundMeterVO;
import com.byy.meterreading.vo.residentmeter.ResidentMeterBindingVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 居民与表具绑定关系业务实现。
 */
@Service
public class ResidentMeterServiceImpl implements ResidentMeterService {

    private static final String RESIDENT_ROLE = "RESIDENT";

    private final ResidentMeterMapper residentMeterMapper;
    private final MeterMapper meterMapper;
    private final SysUserService sysUserService;

    public ResidentMeterServiceImpl(
            ResidentMeterMapper residentMeterMapper,
            MeterMapper meterMapper,
            SysUserService sysUserService
    ) {
        this.residentMeterMapper = residentMeterMapper;
        this.meterMapper = meterMapper;
        this.sysUserService = sysUserService;
    }

    /**
     * 校验居民、角色和表具状态后创建绑定，联合主键负责并发重复绑定兜底。
     */
    @Override
    @Transactional
    public ResidentMeterBindingVO bindMeter(
            Long operatorId,
            BindResidentMeterDTO bindDTO
    ) {
        requireOperatorId(operatorId);
        SysUser resident = requireResident(bindDTO.residentId(), true);
        Meter meter = requireMeter(bindDTO.meterId());

        if (MeterStatus.fromCode(meter.getStatus()) != MeterStatus.ACTIVE) {
            throw new IllegalArgumentException("只有正常状态的表具可以绑定居民");
        }
        if (existsBinding(bindDTO.residentId(), bindDTO.meterId())) {
            throw new ResourceConflictException("居民已经绑定该表具");
        }

        LocalDateTime boundAt = LocalDateTime.now();
        ResidentMeter binding = ResidentMeter.builder()
                .residentId(bindDTO.residentId())
                .meterId(bindDTO.meterId())
                .createdBy(operatorId)
                .createdAt(boundAt)
                .build();
        try {
            residentMeterMapper.insert(binding);
        } catch (DuplicateKeyException exception) {
            throw new ResourceConflictException(
                    "居民已经绑定该表具",
                    exception
            );
        }

        return toBindingVO(resident, meter, operatorId, boundAt);
    }

    /**
     * 当前绑定关系尚未承载业务记录，可以直接删除中间表记录。
     * 抄表任务模块建立后，在删除前继续增加未完成任务检查。
     */
    @Override
    @Transactional
    public void unbindMeter(
            Long operatorId,
            Long residentId,
            Long meterId
    ) {
        requireOperatorId(operatorId);
        requirePositiveId(residentId, "居民ID必须大于0");
        requirePositiveId(meterId, "表具ID必须大于0");

        int deletedRows = residentMeterMapper.delete(
                Wrappers.<ResidentMeter>lambdaQuery()
                        .eq(ResidentMeter::getResidentId, residentId)
                        .eq(ResidentMeter::getMeterId, meterId)
        );
        if (deletedRows != 1) {
            throw new ResourceNotFoundException("居民表具绑定关系不存在");
        }
    }

    /**
     * 管理员查询完整绑定关系，底层使用三表 JOIN 和数据库分页。
     */
    @Override
    @Transactional(readOnly = true)
    public PageVO<ResidentMeterBindingVO> listBindings(
            ResidentMeterPageQueryDTO queryDTO
    ) {
        Page<ResidentMeterBindingRow> page = new Page<>(
                queryDTO.page(),
                queryDTO.pageSize()
        );
        IPage<ResidentMeterBindingRow> result =
                residentMeterMapper.selectBindingPage(
                        page,
                        queryDTO.residentId(),
                        queryDTO.meterId(),
                        queryDTO.keyword()
                );

        return new PageVO<>(
                result.getRecords().stream()
                        .map(this::toBindingVO)
                        .toList(),
                result.getTotal(),
                result.getCurrent(),
                result.getSize()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageVO<ResidentBoundMeterVO> listResidentMeters(
            Long residentId,
            BindingResourcePageQueryDTO queryDTO
    ) {
        requireResident(residentId, false);
        return selectResidentMeterPage(residentId, queryDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public PageVO<MeterBoundResidentVO> listMeterResidents(
            Long meterId,
            BindingResourcePageQueryDTO queryDTO
    ) {
        requireMeter(meterId);
        Page<MeterBoundResidentRow> page = new Page<>(
                queryDTO.page(),
                queryDTO.pageSize()
        );
        IPage<MeterBoundResidentRow> result =
                residentMeterMapper.selectMeterResidentPage(
                        page,
                        meterId,
                        queryDTO.keyword()
                );

        return new PageVO<>(
                result.getRecords().stream()
                        .map(this::toMeterBoundResidentVO)
                        .toList(),
                result.getTotal(),
                result.getCurrent(),
                result.getSize()
        );
    }

    /**
     * 当前居民 ID 只能来自已经验证的 JWT，不能由前端请求参数指定。
     */
    @Override
    @Transactional(readOnly = true)
    public PageVO<ResidentBoundMeterVO> listCurrentResidentMeters(
            Long currentResidentId,
            BindingResourcePageQueryDTO queryDTO
    ) {
        requireResident(currentResidentId, true);
        return selectResidentMeterPage(currentResidentId, queryDTO);
    }

    @Override
    public boolean hasBindingsByMeterId(Long meterId) {
        if (meterId == null) {
            return false;
        }
        return residentMeterMapper.selectCount(
                Wrappers.<ResidentMeter>lambdaQuery()
                        .eq(ResidentMeter::getMeterId, meterId)
        ) > 0;
    }

    @Override
    public boolean hasBindingsByResidentId(Long residentId) {
        if (residentId == null) {
            return false;
        }
        return residentMeterMapper.selectCount(
                Wrappers.<ResidentMeter>lambdaQuery()
                        .eq(ResidentMeter::getResidentId, residentId)
        ) > 0;
    }

    private PageVO<ResidentBoundMeterVO> selectResidentMeterPage(
            Long residentId,
            BindingResourcePageQueryDTO queryDTO
    ) {
        Page<ResidentBoundMeterRow> page = new Page<>(
                queryDTO.page(),
                queryDTO.pageSize()
        );
        IPage<ResidentBoundMeterRow> result =
                residentMeterMapper.selectResidentMeterPage(
                        page,
                        residentId,
                        queryDTO.keyword()
                );

        return new PageVO<>(
                result.getRecords().stream()
                        .map(this::toResidentBoundMeterVO)
                        .toList(),
                result.getTotal(),
                result.getCurrent(),
                result.getSize()
        );
    }

    private SysUser requireResident(Long residentId, boolean requireEnabled) {
        requirePositiveId(residentId, "居民ID必须大于0");
        SysUser resident = sysUserService.findById(residentId);
        if (resident == null) {
            throw new ResourceNotFoundException("居民用户不存在");
        }
        List<String> roles = sysUserService.findRoleCodesByUserId(residentId);
        if (!roles.contains(RESIDENT_ROLE)) {
            throw new IllegalArgumentException("指定用户不是居民");
        }
        if (requireEnabled && !Integer.valueOf(1).equals(resident.getStatus())) {
            throw new IllegalArgumentException("居民账号已被禁用");
        }
        return resident;
    }

    private Meter requireMeter(Long meterId) {
        requirePositiveId(meterId, "表具ID必须大于0");
        Meter meter = meterMapper.selectById(meterId);
        if (meter == null) {
            throw new ResourceNotFoundException("表具不存在");
        }
        return meter;
    }

    private boolean existsBinding(Long residentId, Long meterId) {
        return residentMeterMapper.selectCount(
                Wrappers.<ResidentMeter>lambdaQuery()
                        .eq(ResidentMeter::getResidentId, residentId)
                        .eq(ResidentMeter::getMeterId, meterId)
        ) > 0;
    }

    private void requireOperatorId(Long operatorId) {
        if (operatorId == null) {
            throw new IllegalArgumentException("当前操作人不能为空");
        }
    }

    private void requirePositiveId(Long id, String message) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private ResidentMeterBindingVO toBindingVO(
            SysUser resident,
            Meter meter,
            Long createdBy,
            LocalDateTime boundAt
    ) {
        return new ResidentMeterBindingVO(
                resident.getId(),
                resident.getUsername(),
                resident.getDisplayName(),
                resident.getStatus(),
                meter.getId(),
                meter.getMeterNo(),
                meter.getMeterName(),
                MeterType.valueOf(meter.getMeterType()),
                MeterDisplayType.valueOf(meter.getDisplayType()),
                meter.getUnit(),
                meter.getStatus(),
                createdBy,
                boundAt
        );
    }

    private ResidentMeterBindingVO toBindingVO(
            ResidentMeterBindingRow row
    ) {
        return new ResidentMeterBindingVO(
                row.getResidentId(),
                row.getUsername(),
                row.getDisplayName(),
                row.getResidentStatus(),
                row.getMeterId(),
                row.getMeterNo(),
                row.getMeterName(),
                MeterType.valueOf(row.getMeterType()),
                MeterDisplayType.valueOf(row.getDisplayType()),
                row.getUnit(),
                row.getMeterStatus(),
                row.getCreatedBy(),
                row.getBoundAt()
        );
    }

    private ResidentBoundMeterVO toResidentBoundMeterVO(
            ResidentBoundMeterRow row
    ) {
        return new ResidentBoundMeterVO(
                row.getMeterId(),
                row.getMeterNo(),
                row.getMeterName(),
                MeterType.valueOf(row.getMeterType()),
                MeterDisplayType.valueOf(row.getDisplayType()),
                row.getUnit(),
                row.getStatus(),
                row.getVersion(),
                row.getBoundAt()
        );
    }

    private MeterBoundResidentVO toMeterBoundResidentVO(
            MeterBoundResidentRow row
    ) {
        return new MeterBoundResidentVO(
                row.getResidentId(),
                row.getUsername(),
                row.getDisplayName(),
                row.getStatus(),
                row.getBoundAt()
        );
    }
}
