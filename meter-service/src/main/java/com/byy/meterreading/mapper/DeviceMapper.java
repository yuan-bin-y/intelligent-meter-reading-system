package com.byy.meterreading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.byy.meterreading.model.Device;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 设备档案 Mapper，当前全部数据库操作使用 MyBatis-Plus。
 */
@Mapper
public interface DeviceMapper extends BaseMapper<Device> {
    /** 同一设备的轮换请求串行处理；行锁由外层事务释放。 */
    @Select("SELECT * FROM device WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    Device selectForUpdate(Long id);
}
