package com.byy.meterreading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.byy.meterreading.model.OperationLog;
import org.apache.ibatis.annotations.Mapper;

/** 操作审计日志的单表新增、分页和详情查询 Mapper。 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
