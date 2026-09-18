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

    /** 设备密钥的 SHA-256 十六进制摘要，绝不保存或返回明文。 */
    private String secretHash;

    /** 设备凭证版本：0 表示尚未配置密钥。 */
    private Integer credentialVersion;

    /** 初次生成或最近一次重置设备密钥的时间。 */
    private LocalDateTime secretRotatedAt;

    private String remark;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
}
