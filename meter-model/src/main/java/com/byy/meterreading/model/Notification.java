package com.byy.meterreading.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 通知持久化实体；SSE 投递失败时仍可从该表恢复通知。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("notification")
public class Notification {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long recipientUserId;
    private Long actorUserId;
    private String notificationType;
    private String resourceType;
    private Long resourceId;
    private String title;
    private String content;
    private String dedupKey;
    private Integer isRead;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
