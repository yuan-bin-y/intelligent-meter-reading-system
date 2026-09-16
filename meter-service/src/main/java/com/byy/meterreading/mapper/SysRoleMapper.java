package com.byy.meterreading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.byy.meterreading.mapper.projection.UserRoleCodeRow;
import com.byy.meterreading.model.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    /**
     * 一次查询多个用户拥有的有效角色，供用户分页列表批量组装角色使用。
     *
     * @param userIds 用户 ID 集合，调用方应保证集合不为空
     * @return 用户 ID 与角色编码的对应关系
     */
    List<UserRoleCodeRow> selectRoleCodesByUserIds(
            @Param("userIds") Collection<Long> userIds
    );
}
