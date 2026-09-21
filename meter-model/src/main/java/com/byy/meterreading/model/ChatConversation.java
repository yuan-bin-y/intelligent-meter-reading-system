package com.byy.meterreading.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 居民、抄表员和管理员之间的聊天会话实体。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_conversation")
public class ChatConversation {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** TASK_SERVICE 或 ADMIN_SUPPORT。 */
    private String conversationType;

    /** 任务沟通会话对应的抄表任务；管理员客服会话为空。 */
    private Long taskId;

    private String subject;

    /** WAITING、ACTIVE 或 CLOSED。 */
    private String status;

    /** 发起会话的用户主键。 */
    private Long createdBy;

    /** 冗余保存最后消息，避免会话列表逐条查询消息表。 */
    private Long lastMessageId;
    private LocalDateTime lastMessageAt;

    private Long closedBy;
    private String closeReason;
    private LocalDateTime closedAt;

    @Version
    private Integer version;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
