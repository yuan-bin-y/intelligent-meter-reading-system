package com.byy.meterreading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.byy.meterreading.model.ChatMessage;
import com.byy.meterreading.vo.chat.ChatMessageVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 聊天消息 Mapper；写操作使用 MyBatis-Plus，历史消息和统计使用 XML。 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    /**
     * 按消息主键倒序查询历史记录；limit 通常为客户端页大小加一。
     */
    List<ChatMessageVO> selectMessageHistory(
            @Param("conversationId") Long conversationId,
            @Param("beforeMessageId") Long beforeMessageId,
            @Param("limit") int limit
    );

    /** 统计当前用户全部有效会话中由其他成员发送的未读消息数。 */
    long countUnreadMessages(@Param("userId") Long userId);

    /** 统计当前用户存在未读消息的有效会话数。 */
    long countUnreadConversations(@Param("userId") Long userId);
}
