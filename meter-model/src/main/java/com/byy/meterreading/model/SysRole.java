package com.byy.meterreading.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 系统角色实体，对应 sys_role 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SysRole {

    private Long id;
    private String roleCode;
    private String roleName;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
