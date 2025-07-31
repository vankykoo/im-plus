package com.vanky.im.message.service.impl;

import com.vanky.im.message.constants.MessageTypeConstants;
import com.vanky.im.message.dto.ConversationOverviewDTO;
import com.vanky.im.message.dto.SyncConversationRequest;
import com.vanky.im.message.mapper.UserConversationListMapper;
import com.vanky.im.message.service.ConversationSyncService;
import com.vanky.im.message.service.UserInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 会话同步服务实现类
 * 
 * @author vanky
 * @since 2025-07-28
 */
// {{CHENGQI:
// Action: Added; Timestamp: 2025-07-28 22:55:09 +08:00; Reason: 创建会话同步服务实现类，实现高效的会话概览查询;
// }}
// {{START MODIFICATIONS}}
@Slf4j
@Service
public class ConversationSyncServiceImpl implements ConversationSyncService {
    
    @Autowired
    private UserConversationListMapper userConversationListMapper;

    @Autowired
    private UserInfoService userInfoService;
    
    @Override
    public List<ConversationOverviewDTO> syncUserConversations(SyncConversationRequest request) {
        log.info("开始同步用户会话概览 - 用户ID: {}, 限制数量: {}", request.getUserId(), request.getLimit());
        
        try {
            List<ConversationOverviewDTO> conversations = getUserConversationOverviews(
                    request.getUserId(), 
                    request.getLimit()
            );
            
            log.info("会话概览同步完成 - 用户ID: {}, 会话数量: {}", request.getUserId(), conversations.size());
            return conversations;
            
        } catch (Exception e) {
            log.error("会话概览同步失败 - 用户ID: {}", request.getUserId(), e);
            throw new RuntimeException("会话概览同步失败", e);
        }
    }
    
    @Override
    public List<ConversationOverviewDTO> getUserConversationOverviews(Long userId, Integer limit) {
        log.debug("执行会话概览查询 - 用户ID: {}, 限制数量: {}", userId, limit);
        
        // 执行高效的JOIN查询
        List<ConversationOverviewDTO> result = userConversationListMapper.selectConversationOverviews(userId, limit);
        
        // 处理会话名称、头像和发送者昵称的逻辑
        for (ConversationOverviewDTO overview : result) {
            processConversationDisplayInfo(overview, userId);
            // 处理最后一条消息发送者昵称
            if (overview.getLastMsgSender() != null) {
                try {
                    Long senderId = Long.valueOf(overview.getLastMsgSender());
                    String senderName = userInfoService.getUsernameById(senderId);
                    overview.setLastMsgSender(senderName);
                } catch (NumberFormatException e) {
                    log.warn("解析发送者ID失败 - 会话ID: {}, 发送者: {}",
                            overview.getConversationId(), overview.getLastMsgSender());
                }
            }
        }
        
        log.debug("会话概览查询完成 - 用户ID: {}, 结果数量: {}", userId, result.size());
        return result;
    }
    
    /**
     * 处理会话显示信息（名称和头像）
     * 
     * @param overview 会话概览
     * @param currentUserId 当前用户ID
     */
    private void processConversationDisplayInfo(ConversationOverviewDTO overview, Long currentUserId) {
        try {
            // 注意：数据库中conversation.type字段：0-私聊，1-群聊
            if (overview.getConversationType() != null && overview.getConversationType() == 0) {
                // 私聊：显示对方的昵称和头像
                processPrivateConversationInfo(overview, currentUserId);
            } else if (overview.getConversationType() != null && overview.getConversationType() == 1) {
                // 群聊：使用群组名称和头像
                processGroupConversationInfo(overview);
            }
        } catch (Exception e) {
            log.warn("处理会话显示信息失败 - 会话ID: {}", overview.getConversationId(), e);
            // 设置默认值，避免显示异常
            if (overview.getConversationName() == null) {
                overview.setConversationName("未知会话");
            }
        }
    }
    
    /**
     * 处理私聊会话信息
     */
    private void processPrivateConversationInfo(ConversationOverviewDTO overview, Long currentUserId) {
        // 从会话ID中解析出对方用户ID
        String conversationId = overview.getConversationId();
        if (conversationId.startsWith("private_")) {
            String[] parts = conversationId.replace("private_", "").split("_");
            if (parts.length == 2) {
                Long otherUserId = parts[0].equals(currentUserId.toString()) ? 
                        Long.valueOf(parts[1]) : Long.valueOf(parts[0]);
                
                // 调用用户服务获取对方用户信息
                String otherUserName = userInfoService.getUsernameById(otherUserId);
                String otherUserAvatar = userInfoService.getUserAvatarById(otherUserId);
                overview.setConversationName(otherUserName);
                overview.setConversationAvatar(otherUserAvatar);
            }
        }
    }
    
    /**
     * 处理群聊会话信息
     */
    private void processGroupConversationInfo(ConversationOverviewDTO overview) {
        // 群聊直接使用数据库中的名称和头像
        if (overview.getConversationName() == null || overview.getConversationName().isEmpty()) {
            overview.setConversationName("群聊");
        }
        if (overview.getConversationAvatar() == null || overview.getConversationAvatar().isEmpty()) {
            overview.setConversationAvatar("/default/group_avatar.png");
        }
    }
}
// {{END MODIFICATIONS}}
