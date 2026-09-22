package com.byy.meterreading.service;

import com.byy.meterreading.dto.audit.OperationLogPageQueryDTO;
import com.byy.meterreading.model.OperationLog;
import com.byy.meterreading.vo.audit.OperationLogDetailVO;
import com.byy.meterreading.vo.audit.OperationLogListVO;
import com.byy.meterreading.vo.common.PageVO;

/** 操作审计日志的独立保存、分页查询和详情查询业务。 */
public interface OperationLogService {

    /** 由审计 AOP 调用，在独立事务中持久化一条操作记录。 */
    void saveLog(OperationLog operationLog);

    /** 管理员按追踪标识、操作人、模块、结果和时间范围分页查询。 */
    PageVO<OperationLogListVO> listLogs(
            OperationLogPageQueryDTO queryDTO
    );

    /** 管理员查询一条操作审计日志的完整详情。 */
    OperationLogDetailVO getLog(Long logId);
}
