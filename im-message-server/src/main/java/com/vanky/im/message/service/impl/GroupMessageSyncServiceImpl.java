package com.vanky.im.message.service.impl;

import com.vanky.im.message.entity.ConversationMsgList;
import com.vanky.im.message.entity.Message;
import com.vanky.im.message.model.MessageInfo;
import com.vanky.im.message.model.request.PullGroupMessagesRequest;
import com.vanky.im.message.model.response.PullGroupMessagesResponse;
import com.vanky.im.message.service.ConversationMsgListService;
import com.vanky.im.message.service.GroupMessageSyncService;
import com.vanky.im.message.service.MessageService;
import com.vanky.im.message.util.MessageConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 群聊消息同步服务实现
 * 用于读扩散模式下的群聊消息拉取
 * 
 * @author vanky
 * @create 2025-08-02
 */
// {{CHENGQI:
// Action: Added; Timestamp: 2025-08-02 21:41:41 +08:00; Reason: 实现群聊消息同步服务，支持读扩散模式的消息拉取;
// }}
// {{START MODIFICATIONS}}
@Slf4j
@Service
public class GroupMessageSyncServiceImpl implements GroupMessageSyncService {
    
    @Autowired
    private ConversationMsgListService conversationMsgListService;
    
    @Autowired
    private MessageService messageService;
    
    @Override
    public PullGroupMessagesResponse pullGroupMessages(PullGroupMessagesRequest request) {
        log.info("开始拉取群聊消息 - 用户ID: {}, 会话数量: {}", 
                request.getUserId(), request.getConversations().size());
        
        PullGroupMessagesResponse response = new PullGroupMessagesResponse();
        Map<String, List<MessageInfo>> conversationMessages = new HashMap<>();
        Map<String, Long> latestSeqs = new HashMap<>();
        int totalCount = 0;
        
        // 遍历每个会话，执行读扩散查询
        for (Map.Entry<String, Long> entry : request.getConversations().entrySet()) {
            String conversationId = entry.getKey();
            Long lastReadSeq = entry.getValue();
            
            try {
                // 1. 查询conversation_msg_list表，获取新消息的ID列表
                List<ConversationMsgList> newMsgList = conversationMsgListService.getMessagesAfterSeq(
                        conversationId, lastReadSeq, request.getLimit());
                
                if (newMsgList.isEmpty()) {
                    // 没有新消息
                    conversationMessages.put(conversationId, new ArrayList<>());
                    latestSeqs.put(conversationId, lastReadSeq);
                    continue;
                }
                
                // 2. 提取消息ID列表
                List<Long> msgIds = newMsgList.stream()
                        .map(ConversationMsgList::getMsgId)
                        .collect(Collectors.toList());
                
                // 3. 批量查询message表，获取完整消息内容
                List<Message> messages = messageService.getByMsgIds(msgIds);

                // 4. 创建msgId到seq的映射
                Map<Long, Long> msgIdToSeqMap = newMsgList.stream()
                        .collect(Collectors.toMap(
                                ConversationMsgList::getMsgId,
                                ConversationMsgList::getSeq
                        ));

                // 5. 转换为MessageInfo并设置seq，然后按seq排序
                List<MessageInfo> messageInfos = messages.stream()
                        .map(message -> {
                            MessageInfo messageInfo = MessageConverter.convertToMessageInfo(message);
                            // 从conversation_msg_list中获取对应的seq
                            Long seq = msgIdToSeqMap.get(message.getMsgId());
                            messageInfo.setSeq(seq);
                            return messageInfo;
                        })
                        .sorted(Comparator.comparing(MessageInfo::getSeq))
                        .collect(Collectors.toList());
                
                conversationMessages.put(conversationId, messageInfos);
                
                // 5. 记录该会话的最新seq
                Long latestSeq = newMsgList.stream()
                        .mapToLong(ConversationMsgList::getSeq)
                        .max()
                        .orElse(lastReadSeq);
                latestSeqs.put(conversationId, latestSeq);
                
                totalCount += messageInfos.size();
                
                log.debug("会话消息拉取完成 - 会话ID: {}, 新消息数: {}, 最新seq: {}", 
                        conversationId, messageInfos.size(), latestSeq);
                
            } catch (Exception e) {
                log.error("拉取会话消息失败 - 会话ID: {}, 最后已读seq: {}", 
                        conversationId, lastReadSeq, e);
                // 出错时返回空列表，不影响其他会话
                conversationMessages.put(conversationId, new ArrayList<>());
                latestSeqs.put(conversationId, lastReadSeq);
            }
        }
        
        response.setConversations(conversationMessages);
        response.setLatestSeqs(latestSeqs);
        response.setTotalCount(totalCount);
        response.setHasMore(false); // 暂不支持分页，后续可扩展
        
        log.info("群聊消息拉取完成 - 用户ID: {}, 总消息数: {}", request.getUserId(), totalCount);
        return response;
    }
    
    @Override
    public Long getLatestSeq(String conversationId) {
        try {
            return conversationMsgListService.getMaxSeq(conversationId);
        } catch (Exception e) {
            log.error("获取会话最新seq失败 - 会话ID: {}", conversationId, e);
            return 0L;
        }
    }
    
    @Override
    public Map<String, Long> getLatestSeqs(List<String> conversationIds) {
        Map<String, Long> result = new HashMap<>();
        
        for (String conversationId : conversationIds) {
            result.put(conversationId, getLatestSeq(conversationId));
        }
        
        return result;
    }
}
// {{END MODIFICATIONS}}
