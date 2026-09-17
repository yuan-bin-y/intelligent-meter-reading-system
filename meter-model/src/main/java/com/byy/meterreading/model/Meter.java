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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 表具档案实体，对应 meter 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("meter")
public class Meter {

    /** 表具主键，由 MySQL 自增生成。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 全局唯一且创建后不可修改的表具编号。 */
    private String meterNo;

    /** 用于后台展示的表具名称。 */
    private String meterName;

    /** 表具类型：WATER、ELECTRIC、GAS。 */
    private String meterType;

    /** 显示类型：LCD、MECHANICAL_ROLLER。 */
    private String displayType;

    /** 计量单位，例如 m³、kWh。 */
    private String unit;

    /** 读数整数位数。 */
    private Integer integerDigits;

    /** 读数小数位数。 */
    private Integer decimalDigits;

    /** 安装时的初始读数。 */
    private BigDecimal initialReading;

    /** 安装日期。 */
    private LocalDate installedAt;

    /** 状态：0-停用，1-正常，2-维护中，3-已报废。 */
    private Integer status;

    /** MyBatis-Plus 乐观锁版本号。 */
    @Version
    private Integer version;

    /** 备注。 */
    private String remark;

    /** 创建人用户主键。 */
    private Long createdBy;

    /** 最后修改人用户主键。 */
    private Long updatedBy;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;

    /** 逻辑删除标记：0-未删除，1-已删除。 */
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
}
