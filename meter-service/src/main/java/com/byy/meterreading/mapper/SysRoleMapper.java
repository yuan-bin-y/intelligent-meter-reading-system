package com.byy.meterreading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.byy.meterreading.model.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);
}
