package com.vanky.im.message.service.impl;

import com.vanky.im.message.service.GroupConversationUpdateService;
import com.vanky.im.message.service.UserConversationListService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 群聊会话更新服务实现
 * 用于读扩散模式下的简化会话视图更新
 * 
 * @author vanky
 * @create 2025-08-02
 */
// {{CHENGQI:
// Action: Added; Timestamp: 2025-08-02 22:08:11 +08:00; Reason: 实现群聊会话更新服务，支持读扩散模式的简化会话视图更新;
// }}
// {{START MODIFICATIONS}}
@Slf4j
@Service
public class GroupConversationUpdateServiceImpl implements GroupConversationUpdateService {
    
    @Autowired
    private UserConversationListService userConversationListService;
    
    @Override
    public void updateGroupConversationView(String conversationId, List<String> groupMemberIds, String lastMsgId) {
        log.debug("开始更新群聊会话视图 - 会话ID: {}, 成员数量: {}", conversationId, groupMemberIds.size());
        
        int successCount = 0;
        int failureCount = 0;
        
        for (String memberId : groupMemberIds) {
            try {
                updateUserGroupConversation(memberId, conversationId, lastMsgId);
                successCount++;
            } catch (Exception e) {
                log.error("更新用户群聊会话失败 - 用户ID: {}, 会话ID: {}", memberId, conversationId, e);
                failureCount++;
            }
        }
        
        log.info("群聊会话视图更新完成 - 会话ID: {}, 成功: {}, 失败: {}", 
                conversationId, successCount, failureCount);
    }
    
    @Override
    public void updateUserGroupConversation(String userId, String conversationId, String lastMsgId) {
        try {
            // 读扩散模式：简化的会话视图更新
            // 只更新last_update_time和last_msg_id，不计算unread_count
            userConversationListService.updateGroupConversationSimple(
                    Long.valueOf(userId), conversationId, Long.valueOf(lastMsgId));
            
            log.debug("用户群聊会话更新成功 - 用户ID: {}, 会话ID: {}", userId, conversationId);
            
        } catch (Exception e) {
            log.error("更新用户群聊会话失败 - 用户ID: {}, 会话ID: {}", userId, conversationId, e);
            throw e;
        }
    }
    
    @Override
    public void updateOnlineMembersConversation(String conversationId, List<String> onlineMemberIds, String lastMsgId) {
        log.debug("开始更新在线成员群聊会话视图 - 会话ID: {}, 在线成员数: {}", 
                conversationId, onlineMemberIds.size());
        
        int successCount = 0;
        int failureCount = 0;
        
        for (String memberId : onlineMemberIds) {
            try {
                updateUserGroupConversation(memberId, conversationId, lastMsgId);
                successCount++;
            } catch (Exception e) {
                log.error("更新在线成员群聊会话失败 - 成员ID: {}, 会话ID: {}", memberId, conversationId, e);
                failureCount++;
            }
        }
        
        log.info("在线成员群聊会话视图更新完成 - 会话ID: {}, 成功: {}, 失败: {}", 
                conversationId, successCount, failureCount);
    }
}
// {{END MODIFICATIONS}}
