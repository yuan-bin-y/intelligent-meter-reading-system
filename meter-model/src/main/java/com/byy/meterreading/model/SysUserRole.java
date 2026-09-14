package com.byy.meterreading.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户角色关联实体，对应 sys_user_role 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SysUserRole {

    private Long userId;
    private Long roleId;
    private LocalDateTime createdAt;
}
