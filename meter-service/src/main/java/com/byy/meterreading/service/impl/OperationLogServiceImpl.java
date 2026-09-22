package com.byy.meterreading.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byy.meterreading.common.exception.ResourceNotFoundException;
import com.byy.meterreading.dto.audit.OperationLogPageQueryDTO;
import com.byy.meterreading.mapper.OperationLogMapper;
import com.byy.meterreading.model.OperationLog;
import com.byy.meterreading.model.enums.OperationResult;
import com.byy.meterreading.service.OperationLogService;
import com.byy.meterreading.vo.audit.OperationLogDetailVO;
import com.byy.meterreading.vo.audit.OperationLogListVO;
import com.byy.meterreading.vo.common.PageVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 操作审计日志保存和管理员查询实现。 */
@Service
public class OperationLogServiceImpl implements OperationLogService {

    private final OperationLogMapper operationLogMapper;

    public OperationLogServiceImpl(
            OperationLogMapper operationLogMapper
    ) {
        this.operationLogMapper = operationLogMapper;
    }

    /**
     * 使用独立事务保存日志，使原业务事务回滚时仍能保留失败记录。
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveLog(OperationLog operationLog) {
        validateOperationLog(operationLog);
        if (operationLog.getCreatedAt() == null) {
            operationLog.setCreatedAt(LocalDateTime.now());
        }
        operationLogMapper.insert(operationLog);
    }

    /** 使用 MyBatis-Plus 完成单表动态条件分页查询。 */
    @Override
    @Transactional(readOnly = true)
    public PageVO<OperationLogListVO> listLogs(
            OperationLogPageQueryDTO queryDTO
    ) {
        if (queryDTO == null) {
            throw new IllegalArgumentException("操作日志查询条件不能为空");
        }
        validateCreatedAtRange(queryDTO);

        String resultName = queryDTO.result() == null
                ? null
                : queryDTO.result().name();
        Page<OperationLog> page = new Page<>(
                queryDTO.page(),
                queryDTO.pageSize()
        );
        IPage<OperationLog> result = operationLogMapper.selectPage(
                page,
                Wrappers.<OperationLog>lambdaQuery()
                        .eq(queryDTO.traceId() != null,
                                OperationLog::getTraceId,
                                queryDTO.traceId())
                        .like(queryDTO.operatorUsername() != null,
                                OperationLog::getOperatorUsername,
                                queryDTO.operatorUsername())
                        .eq(queryDTO.module() != null,
                                OperationLog::getModule,
                                queryDTO.module())
                        .eq(resultName != null,
                                OperationLog::getResult,
                                resultName)
                        .ge(queryDTO.createdAtStart() != null,
                                OperationLog::getCreatedAt,
                                queryDTO.createdAtStart())
                        .le(queryDTO.createdAtEnd() != null,
                                OperationLog::getCreatedAt,
                                queryDTO.createdAtEnd())
                        .orderByDesc(
                                OperationLog::getCreatedAt,
                                OperationLog::getId
                        )
        );

        return new PageVO<>(
                result.getRecords().stream()
                        .map(this::toListVO)
                        .toList(),
                result.getTotal(),
                result.getCurrent(),
                result.getSize()
        );
    }

    /** 查询单条日志；不存在时向管理员返回统一的资源不存在错误。 */
    @Override
    @Transactional(readOnly = true)
    public OperationLogDetailVO getLog(Long logId) {
        if (logId == null || logId <= 0) {
            throw new IllegalArgumentException("操作日志ID必须大于0");
        }

        OperationLog operationLog = operationLogMapper.selectById(logId);
        if (operationLog == null) {
            throw new ResourceNotFoundException("操作审计日志不存在");
        }
        return toDetailVO(operationLog);
    }

    /** 防止 AOP 组装不完整的数据进入审计表。 */
    private void validateOperationLog(OperationLog operationLog) {
        if (operationLog == null) {
            throw new IllegalArgumentException("操作审计日志不能为空");
        }
        requireText(operationLog.getTraceId(), "traceId不能为空");
        requireText(operationLog.getModule(), "业务模块不能为空");
        requireText(operationLog.getAction(), "操作动作不能为空");
        requireText(operationLog.getRequestMethod(), "请求方法不能为空");
        requireText(operationLog.getRequestPath(), "请求路径不能为空");
        toOperationResult(operationLog.getResult());
        if (operationLog.getDurationMs() == null
                || operationLog.getDurationMs() < 0) {
            throw new IllegalArgumentException("执行耗时不能小于0");
        }
    }

    private void validateCreatedAtRange(
            OperationLogPageQueryDTO queryDTO
    ) {
        if (queryDTO.createdAtStart() != null
                && queryDTO.createdAtEnd() != null
                && queryDTO.createdAtStart()
                        .isAfter(queryDTO.createdAtEnd())) {
            throw new IllegalArgumentException(
                    "开始时间不能晚于结束时间"
            );
        }
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private OperationLogListVO toListVO(OperationLog operationLog) {
        OperationResult result = toOperationResult(
                operationLog.getResult()
        );
        return new OperationLogListVO(
                operationLog.getId(),
                operationLog.getTraceId(),
                operationLog.getOperatorId(),
                operationLog.getOperatorUsername(),
                operationLog.getModule(),
                operationLog.getAction(),
                operationLog.getResourceType(),
                operationLog.getResourceId(),
                result,
                result.getDescription(),
                operationLog.getDurationMs(),
                operationLog.getCreatedAt()
        );
    }

    private OperationLogDetailVO toDetailVO(
            OperationLog operationLog
    ) {
        OperationResult result = toOperationResult(
                operationLog.getResult()
        );
        return new OperationLogDetailVO(
                operationLog.getId(),
                operationLog.getTraceId(),
                operationLog.getOperatorId(),
                operationLog.getOperatorUsername(),
                operationLog.getModule(),
                operationLog.getAction(),
                operationLog.getResourceType(),
                operationLog.getResourceId(),
                operationLog.getRequestMethod(),
                operationLog.getRequestPath(),
                operationLog.getClientIp(),
                operationLog.getRequestParams(),
                result,
                result.getDescription(),
                operationLog.getErrorMessage(),
                operationLog.getDurationMs(),
                operationLog.getCreatedAt()
        );
    }

    private OperationResult toOperationResult(String result) {
        if (result == null) {
            throw new IllegalArgumentException("操作结果不能为空");
        }
        try {
            return OperationResult.valueOf(result);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("操作结果不合法", exception);
        }
    }
}
