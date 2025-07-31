package com.vanky.im.message.service;

import java.util.List;

/**
 * 消息接收者处理服务接口
 * 负责处理新消息时对所有接收者的统一逻辑
 * 
 * @author vanky
 * @since 2025-07-28
 */
// {{CHENGQI:
// Action: Added; Timestamp: 2025-07-28 23:08:31 +08:00; Reason: 创建统一的消息接收者处理服务接口;
// }}
// {{START MODIFICATIONS}}
public interface MessageReceiverService {
    
    /**
     * 处理新消息的接收者逻辑
     * 对于会话中的每个接收者：
     * a. 向其 user_msg_list 表中插入一条记录，并获得一个新的、该用户专属的 seq (用户级全局seq)
     * b. 更新其 user_conversation_list 表中对应的会话记录：
     *    - unread_count = unread_count + 1
     *    - last_msg_id = (新消息的msg_id)
     *    - last_update_time = NOW()
     * 
     * @param msgId 消息ID
     * @param conversationId 会话ID
     * @param receiverIds 接收者用户ID列表
     */
    void processMessageReceivers(String msgId, String conversationId, List<String> receiverIds);
    
    /**
     * 处理单个接收者的消息逻辑
     * 
     * @param userId 接收者用户ID
     * @param msgId 消息ID
     * @param conversationId 会话ID
     * @return 该用户的全局seq
     */
    Long processSingleReceiver(String userId, String msgId, String conversationId);
}
// {{END MODIFICATIONS}}
