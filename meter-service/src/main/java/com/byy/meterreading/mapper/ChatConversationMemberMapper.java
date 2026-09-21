package com.byy.meterreading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.byy.meterreading.model.ChatConversationMember;
import com.byy.meterreading.vo.chat.ChatMemberVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话成员 Mapper；负责成员关系、成员详情和原子推进已读游标。
 */
@Mapper
public interface ChatConversationMemberMapper
        extends BaseMapper<ChatConversationMember> {

    /** 关联用户表查询会话全部成员，包含已经离开的历史处理人。 */
    List<ChatMemberVO> selectConversationMembers(
            @Param("conversationId") Long conversationId
    );

    /**
     * 仅当新消息 ID 大于原游标时更新，防止并发旧请求使读取进度倒退。
     */
    int advanceReadCursor(
            @Param("conversationId") Long conversationId,
            @Param("userId") Long userId,
            @Param("lastReadMessageId") Long lastReadMessageId,
            @Param("lastReadAt") LocalDateTime lastReadAt
    );
}
