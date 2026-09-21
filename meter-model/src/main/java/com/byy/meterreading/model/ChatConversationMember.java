package com.byy.meterreading.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 聊天会话成员和已读游标实体。
 *
 * <p>数据库主键是 conversation_id 与 user_id 的联合主键，因此该实体
 * 不声明单独的 @TableId。后续使用条件查询或自定义 Mapper 操作。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_conversation_member")
public class ChatConversationMember {

    private Long conversationId;
    private Long userId;

    /** RESIDENT、METER_READER 或 ADMIN。 */
    private String memberRole;

    /** 使用消息主键作为读取进度，无需逐条更新消息已读状态。 */
    private Long lastReadMessageId;
    private LocalDateTime lastReadAt;

    private LocalDateTime joinedAt;
    private LocalDateTime leftAt;
}
