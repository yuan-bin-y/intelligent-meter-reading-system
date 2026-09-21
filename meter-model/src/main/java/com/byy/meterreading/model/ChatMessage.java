package com.byy.meterreading.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 聊天消息持久化实体。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_message")
public class ChatMessage {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    /** 客户端生成的消息编号，用于断线重发时保证幂等。 */
    private String clientMessageId;

    /** 系统消息没有实际用户，因此发送人主键为空。 */
    private Long senderId;

    /** RESIDENT、METER_READER、ADMIN 或 SYSTEM。 */
    private String senderRole;

    /** TEXT 或 SYSTEM。 */
    private String messageType;

    private String content;

    /** 0-正常，1-逻辑删除。 */
    private Integer deleted;
    private Long deletedBy;
    private LocalDateTime deletedAt;

    private LocalDateTime createdAt;
}
