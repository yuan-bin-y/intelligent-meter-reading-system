package com.byy.meterreading.service;

import com.byy.meterreading.dto.chat.ChatConversationPageQueryDTO;
import com.byy.meterreading.dto.chat.ChatMessageCursorQueryDTO;
import com.byy.meterreading.dto.chat.ClaimChatConversationDTO;
import com.byy.meterreading.dto.chat.CloseChatConversationDTO;
import com.byy.meterreading.dto.chat.CreateAdminSupportConversationDTO;
import com.byy.meterreading.dto.chat.CreateTaskConversationDTO;
import com.byy.meterreading.dto.chat.ReadChatMessageDTO;
import com.byy.meterreading.dto.chat.TransferChatConversationDTO;
import com.byy.meterreading.vo.chat.ChatConversationDetailVO;
import com.byy.meterreading.vo.chat.ChatConversationListVO;
import com.byy.meterreading.vo.chat.ChatConversationVO;
import com.byy.meterreading.vo.chat.ChatMessagePageVO;
import com.byy.meterreading.vo.chat.ChatReadReceiptVO;
import com.byy.meterreading.vo.chat.ChatUnreadCountVO;
import com.byy.meterreading.vo.common.PageVO;

/** 居民、抄表员和管理员之间的聊天会话业务。 */
public interface ChatService {

    ChatConversationVO createTaskConversation(
            Long currentUserId,
            CreateTaskConversationDTO createDTO
    );

    ChatConversationVO createAdminSupportConversation(
            Long residentId,
            CreateAdminSupportConversationDTO createDTO
    );

    PageVO<ChatConversationListVO> listMyConversations(
            Long currentUserId,
            ChatConversationPageQueryDTO queryDTO
    );

    ChatConversationDetailVO getConversation(
            Long currentUserId,
            Long conversationId
    );

    ChatMessagePageVO listMessages(
            Long currentUserId,
            Long conversationId,
            ChatMessageCursorQueryDTO queryDTO
    );

    ChatReadReceiptVO markRead(
            Long currentUserId,
            Long conversationId,
            ReadChatMessageDTO readDTO
    );

    ChatUnreadCountVO getUnreadCount(Long currentUserId);

    ChatConversationVO closeConversation(
            Long currentUserId,
            Long conversationId,
            CloseChatConversationDTO closeDTO
    );

    PageVO<ChatConversationListVO> listWaitingAdminConversations(
            ChatConversationPageQueryDTO queryDTO
    );

    ChatConversationVO claimConversation(
            Long adminId,
            Long conversationId,
            ClaimChatConversationDTO claimDTO
    );

    ChatConversationVO transferConversation(
            Long adminId,
            Long conversationId,
            TransferChatConversationDTO transferDTO
    );

    PageVO<ChatConversationListVO> listAdminConversations(
            ChatConversationPageQueryDTO queryDTO
    );
}
