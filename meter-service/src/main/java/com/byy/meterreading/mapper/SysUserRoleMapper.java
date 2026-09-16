package com.byy.meterreading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.byy.meterreading.model.SysUserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;

@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    /**
     * 一次插入多条用户角色关系。
     *
     * @param userRoles 用户角色关系集合
     * @return 插入行数
     */
    int insertBatch(@Param("userRoles") Collection<SysUserRole> userRoles);
}
