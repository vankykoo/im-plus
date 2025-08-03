package com.vanky.im.message.service;

import java.util.List;

/**
 * 群聊会话更新服务接口
 * 用于读扩散模式下的简化会话视图更新
 * 
 * @author vanky
 * @create 2025-08-02
 */
// {{CHENGQI:
// Action: Added; Timestamp: 2025-08-02 22:08:11 +08:00; Reason: 创建群聊会话更新服务接口，支持读扩散模式的简化会话视图更新;
// }}
// {{START MODIFICATIONS}}
public interface GroupConversationUpdateService {
    
    /**
     * 简化更新群聊会话视图（读扩散模式）
     * 只更新last_update_time，不计算unread_count
     * 
     * @param conversationId 会话ID
     * @param groupMemberIds 群成员ID列表
     * @param lastMsgId 最新消息ID
     */
    void updateGroupConversationView(String conversationId, List<String> groupMemberIds, String lastMsgId);
    
    /**
     * 为单个用户更新群聊会话视图
     * 
     * @param userId 用户ID
     * @param conversationId 会话ID
     * @param lastMsgId 最新消息ID
     */
    void updateUserGroupConversation(String userId, String conversationId, String lastMsgId);
    
    /**
     * 批量更新在线用户的群聊会话视图
     * 
     * @param conversationId 会话ID
     * @param onlineMemberIds 在线成员ID列表
     * @param lastMsgId 最新消息ID
     */
    void updateOnlineMembersConversation(String conversationId, List<String> onlineMemberIds, String lastMsgId);
}
// {{END MODIFICATIONS}}
