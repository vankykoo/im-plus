package com.vanky.im.message.processor;

import com.vanky.im.common.protocol.ChatMessage;
import com.vanky.im.message.constant.SessionConstants;
import com.vanky.im.message.entity.ConversationMsgList;
import com.vanky.im.message.entity.GroupMessage;
import com.vanky.im.message.model.UserSession;
import com.vanky.im.message.service.*;
import com.vanky.im.message.util.MessageConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * 群聊消息处理器
 * 实现读扩散模式的消息存储逻辑
 */
@Slf4j
@Component
public class GroupMessageProcessor {

    @Autowired
    private RedisService redisService;
    
    @Autowired
    private GroupMessageService groupMessageService;
    
    @Autowired
    private ConversationMsgListService conversationMsgListService;
    
    @Autowired
    private ConversationService conversationService;
    
    @Autowired
    private GroupMemberService groupMemberService;
    
    @Autowired
    private GatewayMessagePushService gatewayMessagePushService;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    // 消息缓存TTL（24小时）
    private static final long MESSAGE_CACHE_TTL = 24 * 60 * 60;
    
    // 用户消息列表缓存的最大数量
    private static final int USER_MSG_CACHE_MAX_SIZE = 100;

    /**
     * 处理群聊消息的读扩散存储
     * @param chatMessage 原始消息
     * @param conversationId 会话ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void processGroupMessage(ChatMessage chatMessage, String conversationId) {
        try {
            String fromUserId = chatMessage.getFromId();
            String groupId = chatMessage.getToId();

            // 1. 生成消息ID（会话ID已由调用方提供）
            String msgId = MessageConverter.generateMsgId();
            
            log.info("开始处理群聊消息 - 会话ID: {}, 消息ID: {}, 发送方: {}, 群组ID: {}", 
                    conversationId, msgId, fromUserId, groupId);
            
            // 2. 校验发送者是否为群成员
            if (!groupMemberService.isGroupMember(groupId, fromUserId)) {
                log.warn("非群成员发送消息 - 用户ID: {}, 群组ID: {}", fromUserId, groupId);
                return;
            }
            
            // 3. 获取群成员数量
            List<String> groupMembers = groupMemberService.getGroupMemberIds(groupId);
            int memberCount = groupMembers.size();
            
            // 4. 处理群聊会话信息（创建或更新）
            conversationService.handleGroupConversation(conversationId, fromUserId, groupId, memberCount);
            log.debug("群聊会话信息处理完成 - 会话ID: {}, 群组ID: {}, 成员数: {}", 
                      conversationId, groupId, memberCount);
            
            // 5. 原子生成Seq
            Long seq = redisService.generateSeq(conversationId);
            log.debug("生成会话序列号 - 会话ID: {}, Seq: {}", conversationId, seq);
            
            // 6. 数据入库（读扩散模式）
            saveMessageData(chatMessage, msgId, conversationId, seq);
            
            // 7. 更新缓存
            updateCache(chatMessage, msgId, conversationId, seq);
            
            // 8. 推送消息给在线群成员
            pushToOnlineMembers(chatMessage, groupId, seq, msgId);
            
            log.info("群聊消息处理完成 - 会话ID: {}, 消息ID: {}, Seq: {}", conversationId, msgId, seq);
            
        } catch (Exception e) {
            log.error("处理群聊消息失败 - 发送方: {}, 群组ID: {}, 原始消息ID: {}", 
                    chatMessage.getFromId(), chatMessage.getToId(), chatMessage.getUid(), e);
            throw e;
        }
    }
    
    /**
     * 保存消息数据到数据库（读扩散）
     * 群聊采用读扩散模式，只保存一条消息记录到group_message表
     * 
     * @param chatMessage 原始消息
     * @param msgId 消息ID
     * @param conversationId 会话ID
     * @param seq 序列号
     */
    private void saveMessageData(ChatMessage chatMessage, String msgId, String conversationId, Long seq) {
        // 1. 保存消息主体到group_message表
        GroupMessage groupMessage = MessageConverter.convertToGroupMessage(chatMessage, msgId, conversationId);
        groupMessageService.save(groupMessage);
        log.debug("保存群聊消息主体完成 - 消息ID: {}", msgId);
        
        // 2. 保存一条会话消息记录到conversation_msg_list表
        ConversationMsgList conversationMsgList = new ConversationMsgList();
        conversationMsgList.setConversationId(conversationId);
        conversationMsgList.setMsgId(Long.valueOf(msgId.hashCode())); // 将String转换为Long
        conversationMsgList.setSeq(seq);
        conversationMsgList.setCreateTime(new Date());
        conversationMsgList.setUpdateTime(new Date());
        conversationMsgListService.save(conversationMsgList);
        log.debug("保存会话消息记录完成 - 会话ID: {}, Seq: {}", conversationId, seq);
    }
    
    /**
     * 更新缓存
     * 缓存消息内容到Redis，便于快速获取
     * 
     * @param chatMessage 原始消息
     * @param msgId 消息ID
     * @param conversationId 会话ID
     * @param seq 序列号
     */
    private void updateCache(ChatMessage chatMessage, String msgId, String conversationId, Long seq) {
        // 1. 将新消息缓存到Redis (String, msgId -> message_json, TTL 1天)
        GroupMessage groupMessage = MessageConverter.convertToGroupMessage(chatMessage, msgId, conversationId);
        String messageJson = MessageConverter.toJson(groupMessage);
        redisService.cacheMessage(msgId, messageJson, MESSAGE_CACHE_TTL);
        
        // 2. 为消息发送者添加消息索引到缓存 (ZSet, user:msg:list:{userId} -> {msgId, seq})
        redisService.addToUserMsgList(chatMessage.getFromId(), msgId, seq, USER_MSG_CACHE_MAX_SIZE);
        
        log.debug("缓存更新完成 - 消息ID: {}, 群组ID: {}", msgId, chatMessage.getToId());
    }
    
    /**
     * 为特定在线用户缓存消息
     * 这是读扩散模式的优化，为活跃用户提前缓存消息索引
     * 
     * @param userId 用户ID
     * @param msgId 消息ID
     * @param seq 序列号
     */
    private void cacheMessageForUser(String userId, String msgId, Long seq) {
        try {
            // 检查用户是否在线
            String userSessionKey = SessionConstants.getUserSessionKey(userId);
            Boolean isOnline = redisTemplate.hasKey(userSessionKey);
            
            // 如果用户在线，为其缓存消息
            if (Boolean.TRUE.equals(isOnline)) {
                redisService.addToUserMsgList(userId, msgId, seq, USER_MSG_CACHE_MAX_SIZE);
                log.debug("为在线用户缓存消息 - 用户ID: {}, 消息ID: {}, Seq: {}", userId, msgId, seq);
            }
        } catch (Exception e) {
            // 缓存错误不影响主要流程
            log.warn("为用户缓存消息失败 - 用户ID: {}, 消息ID: {}", userId, msgId, e);
        }
    }
    
    /**
     * 推送消息给在线群成员
     * 遍历所有在线群成员，将消息推送到其所在网关
     * 
     * @param chatMessage 原始消息
     * @param groupId 群组ID
     * @param seq 序列号
     * @param msgId 消息ID
     */
    private void pushToOnlineMembers(ChatMessage chatMessage, String groupId, Long seq, String msgId) {
        // 获取在线群成员
        Set<String> onlineMembers = groupMemberService.getOnlineGroupMembers(groupId);
        log.debug("准备推送群聊消息给在线成员 - 群组ID: {}, 在线成员数: {}", groupId, onlineMembers.size());
        
        // 遍历在线成员，推送消息
        for (String memberId : onlineMembers) {
            try {
                // 跳过发送者自己
                if (memberId.equals(chatMessage.getFromId())) {
                    continue;
                }
                
                // 获取成员的会话信息
                String userSessionKey = SessionConstants.getUserSessionKey(memberId);
                UserSession userSession = (UserSession) redisTemplate.opsForValue().get(userSessionKey);
                
                if (userSession != null && userSession.getNodeId() != null) {
                    // 推送消息到成员所在的网关
                    gatewayMessagePushService.pushMessageToGateway(chatMessage, seq, userSession.getNodeId());
                    log.debug("推送群聊消息到成员 - 成员ID: {}, 网关ID: {}", memberId, userSession.getNodeId());
                    
                    // 为活跃用户添加消息缓存
                    cacheMessageForUser(memberId, msgId, seq);
                }
            } catch (Exception e) {
                log.error("推送群聊消息给成员失败 - 成员ID: {}", memberId, e);
                // 继续处理其他成员，不影响整体流程
            }
        }
    }
}