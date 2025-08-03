package com.vanky.im.message.service;

import com.vanky.im.message.model.request.PullGroupMessagesRequest;
import com.vanky.im.message.model.response.PullGroupMessagesResponse;

/**
 * 群聊消息同步服务接口
 * 用于读扩散模式下的群聊消息拉取
 * 
 * @author vanky
 * @create 2025-08-02
 */
// {{CHENGQI:
// Action: Added; Timestamp: 2025-08-02 21:41:41 +08:00; Reason: 创建群聊消息同步服务接口，支持读扩散模式的消息拉取;
// }}
// {{START MODIFICATIONS}}
public interface GroupMessageSyncService {
    
    /**
     * 拉取群聊消息（读扩散模式）
     * 
     * 工作流程：
     * 1. 遍历请求中的每个conversation_id
     * 2. 查询conversation_msg_list表，获取seq > last_read_seq的消息ID列表
     * 3. 批量查询message表，获取完整的消息内容
     * 4. 按conversation_id分组返回结果
     * 
     * @param request 拉取请求，包含用户ID和各群聊的已读状态
     * @return 按会话分组的消息列表
     */
    PullGroupMessagesResponse pullGroupMessages(PullGroupMessagesRequest request);
    
    /**
     * 获取指定会话的最新序列号
     * 
     * @param conversationId 会话ID
     * @return 该会话当前的最大seq，如果会话不存在返回0
     */
    Long getLatestSeq(String conversationId);
    
    /**
     * 批量获取多个会话的最新序列号
     * 
     * @param conversationIds 会话ID列表
     * @return Key为会话ID，Value为最大seq的映射
     */
    java.util.Map<String, Long> getLatestSeqs(java.util.List<String> conversationIds);
}
// {{END MODIFICATIONS}}
