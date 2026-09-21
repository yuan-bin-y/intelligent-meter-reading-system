package com.byy.meterreading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byy.meterreading.model.ChatConversation;
import com.byy.meterreading.vo.chat.ChatConversationDetailVO;
import com.byy.meterreading.vo.chat.ChatConversationListVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 聊天会话 Mapper；简单写操作使用 MyBatis-Plus，多表查询使用 XML。
 */
@Mapper
public interface ChatConversationMapper
        extends BaseMapper<ChatConversation> {

    /** 根据会话类型和抄表任务查询唯一的任务沟通会话。 */
    ChatConversation selectByTaskId(
            @Param("conversationType") String conversationType,
            @Param("taskId") Long taskId
    );

    /** 在写事务中锁定会话，防止认领、转交和关闭操作互相覆盖。 */
    ChatConversation selectByIdForUpdate(
            @Param("conversationId") Long conversationId
    );

    /** 分页查询当前用户仍是有效成员的会话及其未读数量。 */
    IPage<ChatConversationListVO> selectMyConversationPage(
            Page<ChatConversationListVO> page,
            @Param("userId") Long userId,
            @Param("conversationType") String conversationType,
            @Param("status") String status
    );

    /** 管理员分页查询系统中的全部会话。 */
    IPage<ChatConversationListVO> selectAdminConversationPage(
            Page<ChatConversationListVO> page,
            @Param("conversationType") String conversationType,
            @Param("status") String status
    );

    /** 关联任务、表具、创建人和关闭人查询会话详情。 */
    ChatConversationDetailVO selectConversationDetail(
            @Param("conversationId") Long conversationId
    );
}
