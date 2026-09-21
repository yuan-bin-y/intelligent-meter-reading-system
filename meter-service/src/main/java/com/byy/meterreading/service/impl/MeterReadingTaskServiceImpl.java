package com.byy.meterreading.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byy.meterreading.common.exception.ResourceConflictException;
import com.byy.meterreading.common.exception.ResourceNotFoundException;
import com.byy.meterreading.common.exception.VersionConflictException;
import com.byy.meterreading.dto.meterreadingtask.AssignMeterReadingTaskDTO;
import com.byy.meterreading.dto.meterreadingtask.CancelMeterReadingTaskDTO;
import com.byy.meterreading.dto.meterreadingtask.CreateMeterReadingTaskDTO;
import com.byy.meterreading.dto.meterreadingtask.MeterReadingTaskPageQueryDTO;
import com.byy.meterreading.dto.meterreadingtask.MeterReadingTaskVersionDTO;
import com.byy.meterreading.mapper.DeviceMapper;
import com.byy.meterreading.mapper.DeviceMeterMapper;
import com.byy.meterreading.mapper.MeterMapper;
import com.byy.meterreading.mapper.MeterReadingTaskMapper;
import com.byy.meterreading.mapper.SysRoleMapper;
import com.byy.meterreading.mapper.SysUserMapper;
import com.byy.meterreading.mapper.projection.MeterReadingTaskDetailRow;
import com.byy.meterreading.mapper.projection.MeterReadingTaskListRow;
import com.byy.meterreading.model.Device;
import com.byy.meterreading.model.DeviceMeter;
import com.byy.meterreading.model.Meter;
import com.byy.meterreading.model.MeterReadingTask;
import com.byy.meterreading.model.SysUser;
import com.byy.meterreading.model.enums.DeviceStatus;
import com.byy.meterreading.model.enums.MeterDisplayType;
import com.byy.meterreading.model.enums.MeterReadingTaskStatus;
import com.byy.meterreading.model.enums.MeterStatus;
import com.byy.meterreading.model.enums.MeterType;
import com.byy.meterreading.model.enums.TaskExecutorType;
import com.byy.meterreading.service.MeterReadingTaskService;
import com.byy.meterreading.service.BusinessNotificationService;
import com.byy.meterreading.vo.common.PageVO;
import com.byy.meterreading.vo.meterreadingtask.MeterReadingTaskDetailVO;
import com.byy.meterreading.vo.meterreadingtask.MeterReadingTaskListItemVO;
import com.byy.meterreading.vo.meterreadingtask.MeterReadingTaskVersionVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 管理员抄表任务管理业务实现。
 */
@Service
public class MeterReadingTaskServiceImpl
        implements MeterReadingTaskService {

    /** sys_user.status 中 1 表示账号启用。 */
    private static final int ENABLED_USER_STATUS = 1;
    /** 人工抄表任务要求用户拥有的有效角色编码。 */
    private static final String METER_READER_ROLE = "METER_READER";
    /** 任务编号固定前缀，后面拼接无连字符 UUID。 */
    private static final String TASK_NO_PREFIX = "MRT-";

    /** 任务单表写入和任务关联查询。 */
    private final MeterReadingTaskMapper meterReadingTaskMapper;
    /** 校验任务关联的表具。 */
    private final MeterMapper meterMapper;
    /** 校验人工执行者账号。 */
    private final SysUserMapper sysUserMapper;
    /** 校验人工执行者是否拥有有效抄表员角色。 */
    private final SysRoleMapper sysRoleMapper;
    /** 校验设备执行者及其启停状态。 */
    private final DeviceMapper deviceMapper;
    /** 校验设备是否已经绑定任务中的表具。 */
    private final DeviceMeterMapper deviceMeterMapper;
    /** 在任务提交成功后为居民和抄表员生成通知。 */
    private final BusinessNotificationService businessNotificationService;

    public MeterReadingTaskServiceImpl(
            MeterReadingTaskMapper meterReadingTaskMapper,
            MeterMapper meterMapper,
            SysUserMapper sysUserMapper,
            SysRoleMapper sysRoleMapper,
            DeviceMapper deviceMapper,
            DeviceMeterMapper deviceMeterMapper,
            BusinessNotificationService businessNotificationService
    ) {
        this.meterReadingTaskMapper = meterReadingTaskMapper;
        this.meterMapper = meterMapper;
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.deviceMapper = deviceMapper;
        this.deviceMeterMapper = deviceMeterMapper;
        this.businessNotificationService = businessNotificationService;
    }

    /**
     * 创建任务前同时校验表具和执行者，避免保存无法执行的任务。
     */
    @Override
    @Transactional
    public MeterReadingTaskDetailVO createTask(
            Long operatorId,
            CreateMeterReadingTaskDTO createDTO
    ) {
        // 1. 操作人只能来自已经通过认证的管理员 JWT。
        requireOperatorId(operatorId);

        // 2. 只有存在且处于正常状态的表具才能创建任务。
        Meter meter = requireActiveMeter(createDTO.meterId());

        // 3. 人工任务校验用户角色；设备任务校验启用状态和表具绑定。
        validateExecutor(
                createDTO.executorType(),
                createDTO.executorId(),
                meter.getId()
        );

        // 4. 所有不可由前端指定的字段都由后端统一生成。
        MeterReadingTask task = MeterReadingTask.builder()
                .taskNo(generateTaskNo())
                .meterId(meter.getId())
                .executorType(createDTO.executorType().name())
                .taskStatus(MeterReadingTaskStatus.PENDING.name())
                .scheduledAt(createDTO.scheduledAt())
                .retryCount(0)
                .version(0)
                .remark(createDTO.remark())
                .createdBy(operatorId)
                .updatedBy(operatorId)
                .build();

        // 5. 将 DTO 的统一 executorId 写入两个互斥数据库字段之一。
        applyExecutor(
                task,
                createDTO.executorType(),
                createDTO.executorId()
        );

        // 6. task_no 的唯一索引作为极低概率 UUID 冲突的最后保护。
        try {
            meterReadingTaskMapper.insert(task);
        } catch (DuplicateKeyException exception) {
            throw new ResourceConflictException(
                    "任务编号发生冲突，请重新创建",
                    exception
            );
        }

        businessNotificationService.notifyTaskAssigned(
                task.getId(),
                operatorId,
                task.getVersion()
        );

        // 7. 使用关联查询返回表具及执行者名称，保证创建和详情结构一致。
        return toDetailVO(requireTaskDetail(task.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageVO<MeterReadingTaskListItemVO> listTasks(
            MeterReadingTaskPageQueryDTO queryDTO
    ) {
        // 1. 在执行 SQL 前拒绝结束时间早于开始时间的无效范围。
        validateScheduledAtRange(queryDTO);

        // 2. Page 参数由 MyBatis-Plus 分页拦截器转换成 LIMIT 查询。
        Page<MeterReadingTaskListRow> page = new Page<>(
                queryDTO.page(),
                queryDTO.pageSize()
        );
        IPage<MeterReadingTaskListRow> result =
                meterReadingTaskMapper.selectTaskPage(
                        page,
                        queryDTO.keyword(),
                        enumName(queryDTO.executorType()),
                        enumName(queryDTO.taskStatus()),
                        queryDTO.meterReaderId(),
                        queryDTO.deviceId(),
                        queryDTO.scheduledAtStart(),
                        queryDTO.scheduledAtEnd()
                );

        // 3. 数据库字符串在返回 API 前转换为枚举并补充中文名称。
        return new PageVO<>(
                result.getRecords().stream()
                        .map(this::toListItemVO)
                        .toList(),
                result.getTotal(),
                result.getCurrent(),
                result.getSize()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public MeterReadingTaskDetailVO getTask(Long taskId) {
        // 路径参数先校验，再通过四表关联查询一次取得完整详情。
        requirePositiveTaskId(taskId);
        return toDetailVO(requireTaskDetail(taskId));
    }

    /**
     * 只有待执行任务允许更换执行者，更新条件同时携带原状态和版本号。
     */
    @Override
    @Transactional
    public MeterReadingTaskVersionVO assignTask(
            Long operatorId,
            Long taskId,
            AssignMeterReadingTaskDTO assignDTO
    ) {
        // 1. 读取任务快照，用于校验版本、状态和当前执行者。
        requireOperatorId(operatorId);
        MeterReadingTask currentTask = requireTask(taskId);
        requireExpectedVersion(currentTask, assignDTO.version());

        // 2. 已经开始的任务不能更换执行者，避免结果归属发生变化。
        requireStatus(
                currentTask,
                MeterReadingTaskStatus.PENDING,
                "只有待执行任务可以重新分配"
        );

        // 3. 新执行者必须满足与创建任务时完全相同的业务要求。
        validateExecutor(
                assignDTO.executorType(),
                assignDTO.executorId(),
                currentTask.getMeterId()
        );
        requireDifferentExecutor(currentTask, assignDTO);

        // 4. 两个数据库执行者字段互斥：设置其中一个时明确清空另一个。
        boolean meterReader = assignDTO.executorType()
                == TaskExecutorType.METER_READER;
        LambdaUpdateWrapper<MeterReadingTask> updateWrapper =
                Wrappers.<MeterReadingTask>lambdaUpdate()
                        .set(MeterReadingTask::getExecutorType,
                                assignDTO.executorType().name())
                        .set(MeterReadingTask::getMeterReaderId,
                                meterReader
                                        ? assignDTO.executorId()
                                        : null)
                        .set(MeterReadingTask::getDeviceId,
                                meterReader
                                        ? null
                                        : assignDTO.executorId())
                        .set(MeterReadingTask::getUpdatedBy, operatorId)
                        .setSql("version = version + 1")
                        .eq(MeterReadingTask::getId, taskId)
                        .eq(MeterReadingTask::getTaskStatus,
                                MeterReadingTaskStatus.PENDING.name())
                        .eq(MeterReadingTask::getVersion,
                                assignDTO.version());

        // 5. id + PENDING + version 条件使并发分配只有一个请求能够成功。
        int updatedRows = meterReadingTaskMapper.update(
                null,
                updateWrapper
        );
        ensureUpdated(updatedRows, taskId);

        // 6. 前端后续操作必须使用递增后的版本号。
        businessNotificationService.notifyTaskAssigned(
                taskId,
                operatorId,
                assignDTO.version() + 1
        );
        return new MeterReadingTaskVersionVO(
                taskId,
                MeterReadingTaskStatus.PENDING,
                assignDTO.version() + 1
        );
    }

    /**
     * 使用状态机决定当前任务是否允许取消，并记录取消原因和时间。
     */
    @Override
    @Transactional
    public MeterReadingTaskVersionVO cancelTask(
            Long operatorId,
            Long taskId,
            CancelMeterReadingTaskDTO cancelDTO
    ) {
        // 1. 读取当前任务并先检查客户端版本是否仍然有效。
        requireOperatorId(operatorId);
        MeterReadingTask currentTask = requireTask(taskId);
        requireExpectedVersion(currentTask, cancelDTO.version());

        // 2. 状态机统一决定 PENDING、PROCESSING、FAILED 是否允许取消。
        MeterReadingTaskStatus currentStatus = taskStatusOf(currentTask);
        if (!currentStatus.canTransitionTo(
                MeterReadingTaskStatus.CANCELLED
        )) {
            throw new IllegalArgumentException(
                    "当前任务状态不允许取消"
            );
        }

        // 3. 取消状态、原因、时间和操作人必须在同一条 SQL 中原子保存。
        LocalDateTime cancelledAt = LocalDateTime.now();
        LambdaUpdateWrapper<MeterReadingTask> updateWrapper =
                Wrappers.<MeterReadingTask>lambdaUpdate()
                        .set(MeterReadingTask::getTaskStatus,
                                MeterReadingTaskStatus.CANCELLED.name())
                        .set(MeterReadingTask::getCancelReason,
                                cancelDTO.reason())
                        .set(MeterReadingTask::getCancelledAt,
                                cancelledAt)
                        .set(MeterReadingTask::getUpdatedBy, operatorId)
                        .setSql("version = version + 1")
                        .eq(MeterReadingTask::getId, taskId)
                        .eq(MeterReadingTask::getTaskStatus,
                                currentStatus.name())
                        .eq(MeterReadingTask::getVersion,
                                cancelDTO.version());

        // 4. 原状态和 version 都放入 WHERE，防止并发状态覆盖。
        int updatedRows = meterReadingTaskMapper.update(
                null,
                updateWrapper
        );
        ensureUpdated(updatedRows, taskId);
        businessNotificationService.notifyTaskStatusChanged(
                taskId,
                operatorId,
                MeterReadingTaskStatus.CANCELLED.getDescription(),
                cancelDTO.version() + 1
        );
        return new MeterReadingTaskVersionVO(
                taskId,
                MeterReadingTaskStatus.CANCELLED,
                cancelDTO.version() + 1
        );
    }

    /**
     * 失败任务重试时回到待执行状态，并清理上一次执行产生的临时状态。
     */
    @Override
    @Transactional
    public MeterReadingTaskVersionVO retryTask(
            Long operatorId,
            Long taskId,
            MeterReadingTaskVersionDTO versionDTO
    ) {
        // 1. 读取任务并校验客户端提交的乐观锁版本。
        requireOperatorId(operatorId);
        MeterReadingTask currentTask = requireTask(taskId);
        requireExpectedVersion(currentTask, versionDTO.version());

        // 2. 状态机只允许 FAILED 回到 PENDING。
        MeterReadingTaskStatus currentStatus = taskStatusOf(currentTask);
        if (!currentStatus.canTransitionTo(
                MeterReadingTaskStatus.PENDING
        )) {
            throw new IllegalArgumentException(
                    "只有执行失败的任务可以重试"
            );
        }

        // 3. 清理上次执行数据，保留原计划时间和任务分配关系重新执行。
        LambdaUpdateWrapper<MeterReadingTask> updateWrapper =
                Wrappers.<MeterReadingTask>lambdaUpdate()
                        .set(MeterReadingTask::getTaskStatus,
                                MeterReadingTaskStatus.PENDING.name())
                        .set(MeterReadingTask::getStartedAt, null)
                        .set(MeterReadingTask::getSubmittedAt, null)
                        .set(MeterReadingTask::getCompletedAt, null)
                        .set(MeterReadingTask::getFailedReason, null)
                        .set(MeterReadingTask::getUpdatedBy, operatorId)
                        .setSql(
                                "retry_count = retry_count + 1, "
                                        + "version = version + 1"
                        )
                        .eq(MeterReadingTask::getId, taskId)
                        .eq(MeterReadingTask::getTaskStatus,
                                MeterReadingTaskStatus.FAILED.name())
                        .eq(MeterReadingTask::getVersion,
                                versionDTO.version());

        // 4. 重试次数和 version 在数据库中直接递增，避免并发丢失更新。
        int updatedRows = meterReadingTaskMapper.update(
                null,
                updateWrapper
        );
        ensureUpdated(updatedRows, taskId);
        businessNotificationService.notifyTaskStatusChanged(
                taskId,
                operatorId,
                MeterReadingTaskStatus.PENDING.getDescription(),
                versionDTO.version() + 1
        );
        return new MeterReadingTaskVersionVO(
                taskId,
                MeterReadingTaskStatus.PENDING,
                versionDTO.version() + 1
        );
    }

    private Meter requireActiveMeter(Long meterId) {
        if (meterId == null || meterId <= 0) {
            throw new IllegalArgumentException("表具ID必须大于0");
        }
        Meter meter = meterMapper.selectById(meterId);
        if (meter == null) {
            throw new ResourceNotFoundException("表具不存在");
        }
        if (MeterStatus.fromCode(meter.getStatus())
                != MeterStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "只有正常状态的表具可以创建抄表任务"
            );
        }
        return meter;
    }

    /**
     * 人工任务校验启用用户和有效角色；设备任务校验设备状态及表具绑定。
     * 该方法被创建任务和重新分配任务共同复用，确保两条入口规则一致。
     */
    private void validateExecutor(
            TaskExecutorType executorType,
            Long executorId,
            Long meterId
    ) {
        if (executorType == null) {
            throw new IllegalArgumentException("执行者类型不能为空");
        }
        if (executorId == null || executorId <= 0) {
            throw new IllegalArgumentException("执行者ID必须大于0");
        }

        switch (executorType) {
            case METER_READER -> validateMeterReader(executorId);
            case DEVICE -> validateDevice(executorId, meterId);
        }
    }

    private void validateMeterReader(Long meterReaderId) {
        // 用户存在只是第一步，还必须同时满足账号启用和角色有效。
        SysUser meterReader = sysUserMapper.selectById(meterReaderId);
        if (meterReader == null) {
            throw new ResourceNotFoundException("抄表员用户不存在");
        }
        if (!Integer.valueOf(ENABLED_USER_STATUS)
                .equals(meterReader.getStatus())) {
            throw new IllegalArgumentException("抄表员账号已被禁用");
        }
        List<String> roleCodes =
                sysRoleMapper.selectRoleCodesByUserId(meterReaderId);
        if (!roleCodes.contains(METER_READER_ROLE)) {
            throw new IllegalArgumentException(
                    "指定用户不具有抄表员角色"
            );
        }
    }

    private void validateDevice(Long deviceId, Long meterId) {
        // MyBatis-Plus 会自动排除已经逻辑删除的设备。
        Device device = deviceMapper.selectById(deviceId);
        if (device == null) {
            throw new ResourceNotFoundException("设备不存在");
        }
        if (!Integer.valueOf(DeviceStatus.ENABLED.getCode())
                .equals(device.getStatus())) {
            throw new IllegalArgumentException(
                    "只有启用状态的设备可以执行抄表任务"
            );
        }
        boolean bound = deviceMeterMapper.selectCount(
                Wrappers.<DeviceMeter>lambdaQuery()
                        .eq(DeviceMeter::getDeviceId, deviceId)
                        .eq(DeviceMeter::getMeterId, meterId)
        ) > 0;
        if (!bound) {
            throw new IllegalArgumentException(
                    "指定设备尚未绑定该表具"
            );
        }
    }

    /**
     * 将 DTO 的统一 executorId 转换为实体中的两个互斥字段。
     */
    private void applyExecutor(
            MeterReadingTask task,
            TaskExecutorType executorType,
            Long executorId
    ) {
        switch (executorType) {
            case METER_READER -> {
                task.setMeterReaderId(executorId);
                task.setDeviceId(null);
            }
            case DEVICE -> {
                task.setMeterReaderId(null);
                task.setDeviceId(executorId);
            }
        }
    }

    private void requireDifferentExecutor(
            MeterReadingTask task,
            AssignMeterReadingTaskDTO assignDTO
    ) {
        if (!assignDTO.executorType().name()
                .equals(task.getExecutorType())) {
            return;
        }
        Long currentExecutorId = assignDTO.executorType()
                == TaskExecutorType.METER_READER
                ? task.getMeterReaderId()
                : task.getDeviceId();
        if (assignDTO.executorId().equals(currentExecutorId)) {
            throw new IllegalArgumentException(
                    "任务已经分配给该执行者"
            );
        }
    }

    private MeterReadingTask requireTask(Long taskId) {
        requirePositiveTaskId(taskId);
        MeterReadingTask task = meterReadingTaskMapper.selectById(taskId);
        if (task == null) {
            throw new ResourceNotFoundException("抄表任务不存在");
        }
        return task;
    }

    private MeterReadingTaskDetailRow requireTaskDetail(Long taskId) {
        MeterReadingTaskDetailRow row =
                meterReadingTaskMapper.selectTaskDetail(taskId);
        if (row == null) {
            throw new ResourceNotFoundException("抄表任务不存在");
        }
        return row;
    }

    private void requireStatus(
            MeterReadingTask task,
            MeterReadingTaskStatus expectedStatus,
            String message
    ) {
        if (taskStatusOf(task) != expectedStatus) {
            throw new IllegalArgumentException(message);
        }
    }

    private MeterReadingTaskStatus taskStatusOf(
            MeterReadingTask task
    ) {
        // 数据库 CHECK 已保证字符串属于枚举取值；这里恢复为状态机枚举。
        return MeterReadingTaskStatus.valueOf(task.getTaskStatus());
    }

    private void requireExpectedVersion(
            MeterReadingTask task,
            Integer expectedVersion
    ) {
        if (expectedVersion == null || expectedVersion < 0) {
            throw new IllegalArgumentException(
                    "数据版本不能为空且不能小于0"
            );
        }
        if (!expectedVersion.equals(task.getVersion())) {
            throw new VersionConflictException(
                    "抄表任务已被其他操作修改，请刷新后重试"
            );
        }
    }

    private void ensureUpdated(int updatedRows, Long taskId) {
        if (updatedRows == 1) {
            return;
        }
        if (meterReadingTaskMapper.selectById(taskId) == null) {
            throw new ResourceNotFoundException("抄表任务不存在");
        }
        // 任务仍存在但更新行数为 0，表示状态或 version 已被并发请求改变。
        throw new VersionConflictException(
                "抄表任务已被其他操作修改，请刷新后重试"
        );
    }

    private void requireOperatorId(Long operatorId) {
        if (operatorId == null) {
            throw new IllegalArgumentException("当前操作人不能为空");
        }
    }

    private void requirePositiveTaskId(Long taskId) {
        if (taskId == null || taskId <= 0) {
            throw new IllegalArgumentException("任务ID必须大于0");
        }
    }

    private void validateScheduledAtRange(
            MeterReadingTaskPageQueryDTO queryDTO
    ) {
        if (queryDTO.scheduledAtStart() != null
                && queryDTO.scheduledAtEnd() != null
                && queryDTO.scheduledAtEnd()
                .isBefore(queryDTO.scheduledAtStart())) {
            throw new IllegalArgumentException(
                    "计划结束时间不能早于开始时间"
            );
        }
    }

    private String generateTaskNo() {
        // UUID 去除连字符后为 32 位，连同前缀不会超过 task_no 的 64 字符限制。
        return TASK_NO_PREFIX + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .toUpperCase();
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private MeterReadingTaskListItemVO toListItemVO(
            MeterReadingTaskListRow row
    ) {
        TaskExecutorType executorType = TaskExecutorType.valueOf(
                row.getExecutorType()
        );
        MeterReadingTaskStatus taskStatus =
                MeterReadingTaskStatus.valueOf(row.getTaskStatus());
        return new MeterReadingTaskListItemVO(
                row.getTaskId(),
                row.getTaskNo(),
                row.getMeterId(),
                row.getMeterNo(),
                row.getMeterName(),
                executorType,
                executorType.getDescription(),
                row.getExecutorId(),
                row.getExecutorName(),
                taskStatus,
                taskStatus.getDescription(),
                row.getScheduledAt(),
                row.getRetryCount(),
                row.getVersion(),
                row.getUpdatedAt()
        );
    }

    private MeterReadingTaskDetailVO toDetailVO(
            MeterReadingTaskDetailRow row
    ) {
        TaskExecutorType executorType = TaskExecutorType.valueOf(
                row.getExecutorType()
        );
        MeterReadingTaskStatus taskStatus =
                MeterReadingTaskStatus.valueOf(row.getTaskStatus());
        return new MeterReadingTaskDetailVO(
                row.getTaskId(),
                row.getTaskNo(),
                row.getMeterId(),
                row.getMeterNo(),
                row.getMeterName(),
                MeterType.valueOf(row.getMeterType()),
                MeterDisplayType.valueOf(row.getDisplayType()),
                row.getUnit(),
                executorType,
                executorType.getDescription(),
                row.getExecutorId(),
                row.getExecutorCode(),
                row.getExecutorName(),
                taskStatus,
                taskStatus.getDescription(),
                row.getScheduledAt(),
                row.getStartedAt(),
                row.getSubmittedAt(),
                row.getCompletedAt(),
                row.getCancelledAt(),
                row.getFailedReason(),
                row.getCancelReason(),
                row.getRetryCount(),
                row.getVersion(),
                row.getRemark(),
                row.getCreatedBy(),
                row.getUpdatedBy(),
                row.getCreatedAt(),
                row.getUpdatedAt()
        );
    }
}
