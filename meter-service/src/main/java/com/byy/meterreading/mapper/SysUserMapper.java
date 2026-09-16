package com.byy.meterreading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byy.meterreading.dto.user.UserPageQueryDTO;
import com.byy.meterreading.model.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 按用户名、状态和角色筛选并分页查询用户。
     */
    IPage<SysUser> selectAdminUserPage(
            Page<SysUser> page,
            @Param("query") UserPageQueryDTO queryDTO
    );
}
