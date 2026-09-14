package com.byy.meterreading.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 系统用户实体，对应 sys_user 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SysUser {

    private Long id;
    private String username;
    private String passwordHash;
    private String displayName;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
