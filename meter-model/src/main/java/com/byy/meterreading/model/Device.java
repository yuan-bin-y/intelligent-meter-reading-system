package com.byy.meterreading.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 采集设备档案实体，对应 device 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("device")
public class Device {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 全局唯一且创建后不可修改的设备编号。 */
    private String deviceNo;

    private String deviceName;

    /** CAMERA、GATEWAY、EDGE_DEVICE。 */
    private String deviceType;

    /** 管理状态：0-停用，1-启用；不代表 Redis 在线状态。 */
    private Integer status;

    @Version
    private Integer version;

    private String remark;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
}
