package com.vanky.im.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanky.im.common.constant.MessageTypeConstants;
import com.vanky.im.message.entity.ConversationMsgList;
import com.vanky.im.message.entity.GroupMessage;
import com.vanky.im.message.entity.Message;
import com.vanky.im.message.entity.PrivateMessage;
import com.vanky.im.message.entity.UserMsgList;
import com.vanky.im.message.mapper.ConversationMapper;
import com.vanky.im.message.mapper.ConversationMsgListMapper;
import com.vanky.im.message.mapper.GroupMessageMapper;
import com.vanky.im.message.mapper.MessageMapper;
import com.vanky.im.message.mapper.PrivateMessageMapper;
import com.vanky.im.message.mapper.UserMsgListMapper;
import com.vanky.im.message.model.dto.MessageDTO;
import com.vanky.im.message.model.dto.MessagePullResponse;
import com.vanky.im.message.service.MessageQueryService;
import com.vanky.im.message.service.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 消息查询服务实现
 */
@Slf4j
@Service
public class MessageQueryServiceImpl implements MessageQueryService {

    @Autowired
    private RedisService redisService;
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    // 注意：以下Mapper已被统一的MessageMapper替代，保留是为了兼容性
    @Autowired
    private PrivateMessageMapper privateMessageMapper;

    @Autowired
    private GroupMessageMapper groupMessageMapper;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private UserMsgListMapper userMsgListMapper;
    
    @Autowired
    private ConversationMsgListMapper conversationMsgListMapper;
    
    @Autowired
    private ConversationMapper conversationMapper;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    // 会话类型常量
    private static final int CONVERSATION_TYPE_PRIVATE = 1;
    private static final int CONVERSATION_TYPE_GROUP = 2;
    
    private static final int DEFAULT_LIMIT = 100;
    
    @Override
    public MessagePullResponse queryMessages(String conversationId, Long startSeq, Long endSeq, Integer limit) {
        if (endSeq == null) {
            endSeq = startSeq + (limit != null && limit > 0 ? limit - 1 : DEFAULT_LIMIT - 1);
        }
        
        if (limit == null || limit <= 0) {
            limit = DEFAULT_LIMIT;
        }
        
        log.info("查询消息 - 会话ID: {}, 序列号范围: [{}, {}], 限制: {}", 
                conversationId, startSeq, endSeq, limit);
        
        // 先尝试从缓存中查询
        List<MessageDTO> messages = queryMessagesFromCache(conversationId, startSeq, endSeq);
        
        // 如果缓存中没有找到足够的消息，再从数据库中查询
        if (messages.size() < (endSeq - startSeq + 1)) {
            log.info("缓存未命中所有消息，从数据库查询 - 会话ID: {}, 缓存命中: {}/{}", 
                    conversationId, messages.size(), (endSeq - startSeq + 1));
            
            // 找出缓存中缺失的序列号范围
            Set<Long> cachedSeqs = messages.stream()
                    .map(msg -> Long.parseLong(msg.getSeq()))
                    .collect(Collectors.toSet());
            
            List<Long> missingSeqs = new ArrayList<>();
            for (long seq = startSeq; seq <= endSeq; seq++) {
                if (!cachedSeqs.contains(seq)) {
                    missingSeqs.add(seq);
                }
            }
            
            // 从数据库查询缺失的消息
            if (!missingSeqs.isEmpty()) {
                List<MessageDTO> dbMessages = queryMessagesFromDb(conversationId, 
                        Collections.min(missingSeqs), Collections.max(missingSeqs), limit);
                
                // 合并结果并按序列号排序
                messages.addAll(dbMessages);
                messages.sort(Comparator.comparing(msg -> Long.parseLong(msg.getSeq())));
                
                // 限制返回数量
                if (messages.size() > limit) {
                    messages = messages.subList(0, limit);
                }
            }
        }
        
        // 构建响应
        MessagePullResponse response = new MessagePullResponse();
        response.setConversationId(conversationId);
        response.setMessages(messages);
        response.setStartSeq(startSeq);
        response.setEndSeq(endSeq);
        response.setCount(messages.size());
        response.setHasMore(messages.size() == limit);
        
        return response;
    }
    
    @Override
    public List<MessageDTO> queryMessagesFromCache(String conversationId, Long startSeq, Long endSeq) {
        List<MessageDTO> result = new ArrayList<>();
        
        try {
            log.debug("从缓存查询消息 - 会话ID: {}, 序列号范围: [{}, {}]", 
                    conversationId, startSeq, endSeq);
            
            // Redis中用户消息链ZSet的键
            String userMsgListKey = "user:msg:list:" + conversationId;
            
            // 查询范围内的所有msgId
            Set<String> msgIds = redisTemplate.opsForZSet().rangeByScore(userMsgListKey, startSeq, endSeq);
            if (msgIds == null || msgIds.isEmpty()) {
                return result;
            }
            
            // 批量获取消息内容
            for (String msgId : msgIds) {
                String messageJson = redisService.getCachedMessage(msgId);
                if (messageJson != null) {
                    try {
                        // 解析消息JSON
                        JsonNode jsonNode = objectMapper.readTree(messageJson);
                        MessageDTO messageDTO = new MessageDTO();
                        
                        // 设置基本字段
                        messageDTO.setType(jsonNode.has("type") ? jsonNode.get("type").asInt() : null);
                        messageDTO.setContent(jsonNode.has("content") ? jsonNode.get("content").asText() : null);
                        messageDTO.setFromId(jsonNode.has("fromId") ? jsonNode.get("fromId").asText() : null);
                        messageDTO.setToId(jsonNode.has("toId") ? jsonNode.get("toId").asText() : null);
                        messageDTO.setUid(msgId);
                        messageDTO.setSeq(jsonNode.has("seq") ? jsonNode.get("seq").asText() : null);
                        messageDTO.setTimestamp(jsonNode.has("timestamp") ? jsonNode.get("timestamp").asLong() : null);
                        messageDTO.setStatus(jsonNode.has("status") ? jsonNode.get("status").asInt() : null);
                        
                        result.add(messageDTO);
                    } catch (Exception e) {
                        log.error("解析缓存消息JSON失败 - msgId: {}", msgId, e);
                    }
                }
            }
            
            // 按序列号排序
            result.sort(Comparator.comparing(msg -> Long.parseLong(msg.getSeq())));
            
            log.debug("从缓存中查询到 {} 条消息", result.size());
            
        } catch (Exception e) {
            log.error("从缓存查询消息失败 - 会话ID: {}, 序列号范围: [{}, {}]", 
                    conversationId, startSeq, endSeq, e);
        }
        
        return result;
    }
    
    /**
     * 判断会话类型
     * @param conversationId 会话ID
     * @return 会话类型：1-私聊，2-群聊
     */
    private int getConversationType(String conversationId) {
        // 通过会话ID前缀快速判断
        if (conversationId.startsWith("group_")) {
            return CONVERSATION_TYPE_GROUP;
        } else if (conversationId.startsWith("private_")) {
            return CONVERSATION_TYPE_PRIVATE;
        }

        // 查询会话表确认类型
        LambdaQueryWrapper<com.vanky.im.message.entity.Conversation> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(com.vanky.im.message.entity.Conversation::getConversationId, conversationId);

        com.vanky.im.message.entity.Conversation conversation = conversationMapper.selectOne(queryWrapper);
        return conversation != null ? conversation.getType() : CONVERSATION_TYPE_PRIVATE; // 默认为私聊
    }
    
    @Override
    public List<MessageDTO> queryMessagesFromDb(String conversationId, Long startSeq, Long endSeq, Integer limit) {
        List<MessageDTO> result = new ArrayList<>();
        
        try {
            log.debug("从数据库查询消息 - 会话ID: {}, 序列号范围: [{}, {}], 限制: {}", 
                    conversationId, startSeq, endSeq, limit);
            
            // 根据会话ID判断会话类型
            int conversationType = getConversationType(conversationId);
            
            if (conversationType == CONVERSATION_TYPE_PRIVATE) {
                // 私聊消息查询（写扩散）- 使用统一的message表
                result = queryUnifiedMessagesFromDb(conversationId, (int)MessageTypeConstants.MSG_TYPE_PRIVATE, startSeq, endSeq, limit);
            } else {
                // 群聊消息查询（读扩散）- 使用统一的message表
                result = queryUnifiedMessagesFromDb(conversationId, (int)MessageTypeConstants.MSG_TYPE_GROUP, startSeq, endSeq, limit);
            }
            
            // 按序列号排序
            result.sort(Comparator.comparing(msg -> Long.parseLong(msg.getSeq())));
            
            log.debug("从数据库中查询到 {} 条消息", result.size());
            
        } catch (Exception e) {
            log.error("从数据库查询消息失败 - 会话ID: {}, 序列号范围: [{}, {}]", 
                    conversationId, startSeq, endSeq, e);
        }
        
        return result;
    }
    
    /**
     * 查询私聊消息（写扩散模式）
     * @param conversationId 会话ID
     * @param startSeq 起始序列号
     * @param endSeq 结束序列号
     * @param limit 消息数量限制
     * @return 私聊消息列表
     */
    private List<MessageDTO> queryPrivateMessagesFromDb(String conversationId, Long startSeq, Long endSeq, Integer limit) {
        List<MessageDTO> result = new ArrayList<>();
        
        // 从user_msg_list表查询符合条件的消息ID
        LambdaQueryWrapper<UserMsgList> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMsgList::getConversationId, conversationId)
               .ge(UserMsgList::getSeq, startSeq)
               .le(UserMsgList::getSeq, endSeq)
               .orderByAsc(UserMsgList::getSeq);
        
        if (limit != null && limit > 0) {
            wrapper.last("LIMIT " + limit);
        }
        
        List<UserMsgList> userMsgLists = userMsgListMapper.selectList(wrapper);
        
        if (userMsgLists.isEmpty()) {
            return result;
        }
        
        // 收集消息ID
        Set<Long> msgIds = userMsgLists.stream()
                .map(UserMsgList::getMsgId)
                .collect(Collectors.toSet());
        
        // 查询消息详情
        LambdaQueryWrapper<PrivateMessage> msgWrapper = new LambdaQueryWrapper<>();
        msgWrapper.in(PrivateMessage::getMsgId, msgIds);
        
        List<PrivateMessage> messages = privateMessageMapper.selectList(msgWrapper);
        Map<Long, PrivateMessage> msgMap = messages.stream()
                .collect(Collectors.toMap(PrivateMessage::getMsgId, msg -> msg));
        
        // 组装结果
        for (UserMsgList userMsg : userMsgLists) {
            PrivateMessage privateMessage = msgMap.get(userMsg.getMsgId());
            if (privateMessage != null) {
                MessageDTO messageDTO = new MessageDTO();
                
                // 手动转换字段
                messageDTO.setType(MessageTypeConstants.PRIVATE_CHAT_MESSAGE);
                messageDTO.setContent(privateMessage.getContent());
                messageDTO.setFromId(String.valueOf(privateMessage.getUserId()));
                
                // 计算toId（从会话ID中提取另一方的ID）
                String fromId = String.valueOf(privateMessage.getUserId());
                String toId;

                if (conversationId.startsWith("private_")) {
                    // 新格式：private_xx_xx
                    String[] idParts = conversationId.substring("private_".length()).split("_");
                    if (idParts.length == 2) {
                        toId = idParts[0].equals(fromId) ? idParts[1] : idParts[0];
                    } else {
                        toId = "unknown";
                    }
                } else {
                    // 兼容旧格式：xx_xx 或 xx-xx
                    String[] idParts = conversationId.split("-|_");
                    if (idParts.length == 2) {
                        toId = idParts[0].equals(fromId) ? idParts[1] : idParts[0];
                    } else {
                        toId = "unknown";
                    }
                }
                
                messageDTO.setToId(toId);
                messageDTO.setUid(String.valueOf(privateMessage.getMsgId()));
                messageDTO.setSeq(String.valueOf(userMsg.getSeq()));
                messageDTO.setTimestamp(privateMessage.getSendTime().getTime());
                messageDTO.setStatus(privateMessage.getStatus());
                
                result.add(messageDTO);
            }
        }
        
        return result;
    }
    
    /**
     * 查询群聊消息（读扩散模式）
     * @param conversationId 会话ID
     * @param startSeq 起始序列号
     * @param endSeq 结束序列号
     * @param limit 消息数量限制
     * @return 群聊消息列表
     */
    private List<MessageDTO> queryGroupMessagesFromDb(String conversationId, Long startSeq, Long endSeq, Integer limit) {
        List<MessageDTO> result = new ArrayList<>();
        
        // 1. 从conversation_msg_list表查询符合条件的消息ID和序列号
        LambdaQueryWrapper<ConversationMsgList> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConversationMsgList::getConversationId, conversationId)
               .ge(ConversationMsgList::getSeq, startSeq)
               .le(ConversationMsgList::getSeq, endSeq)
               .orderByAsc(ConversationMsgList::getSeq);
        
        if (limit != null && limit > 0) {
            wrapper.last("LIMIT " + limit);
        }
        
        List<ConversationMsgList> conversationMsgLists = conversationMsgListMapper.selectList(wrapper);
        
        if (conversationMsgLists.isEmpty()) {
            return result;
        }
        
        // 2. 收集消息ID
        Set<Long> msgIds = conversationMsgLists.stream()
                .map(ConversationMsgList::getMsgId)
                .collect(Collectors.toSet());
        
        // 3. 查询群聊消息详情
        LambdaQueryWrapper<GroupMessage> msgWrapper = new LambdaQueryWrapper<>();
        msgWrapper.in(GroupMessage::getMsgId, msgIds);
        
        List<GroupMessage> messages = groupMessageMapper.selectList(msgWrapper);
        Map<Long, GroupMessage> msgMap = messages.stream()
                .collect(Collectors.toMap(GroupMessage::getMsgId, msg -> msg));
        
        // 4. 提取群组ID（从会话ID中）
        String groupId = conversationId;
        if (conversationId.startsWith("group_")) {
            groupId = conversationId.substring("group_".length());
        }
        
        // 5. 组装结果
        for (ConversationMsgList conversationMsg : conversationMsgLists) {
            GroupMessage groupMessage = msgMap.get(conversationMsg.getMsgId());
            if (groupMessage != null) {
                MessageDTO messageDTO = new MessageDTO();
                
                messageDTO.setType(MessageTypeConstants.GROUP_CHAT_MESSAGE);
                messageDTO.setContent(groupMessage.getContent());
                messageDTO.setFromId(String.valueOf(groupMessage.getUserId()));
                messageDTO.setToId(groupId); // 接收方为群组ID
                messageDTO.setUid(String.valueOf(groupMessage.getMsgId()));
                messageDTO.setSeq(String.valueOf(conversationMsg.getSeq()));
                messageDTO.setTimestamp(groupMessage.getSendTime().getTime());
                messageDTO.setStatus(groupMessage.getStatus());
                
                result.add(messageDTO);
            }
        }
        
        return result;
    }

    /**
     * 从统一的message表查询消息（新方法）
     * @param conversationId 会话ID
     * @param msgType 消息类型
     * @param startSeq 起始序列号
     * @param endSeq 结束序列号
     * @param limit 限制数量
     * @return 消息列表
     */
    private List<MessageDTO> queryUnifiedMessagesFromDb(String conversationId, Integer msgType,
                                                       Long startSeq, Long endSeq, Integer limit) {
        List<MessageDTO> result = new ArrayList<>();

        try {
            if (MessageTypeConstants.isPrivateMessage(msgType != null ? msgType.byteValue() : null)) {
                // 私聊消息查询（写扩散）
                result = queryPrivateMessagesFromUnifiedTable(conversationId, startSeq, endSeq, limit);
            } else if (MessageTypeConstants.isGroupMessage(msgType != null ? msgType.byteValue() : null)) {
                // 群聊消息查询（读扩散）
                result = queryGroupMessagesFromUnifiedTable(conversationId, startSeq, endSeq, limit);
            }

            log.debug("从统一message表查询到 {} 条消息，消息类型: {}", result.size(), msgType);

        } catch (Exception e) {
            log.error("从统一message表查询消息失败 - 会话ID: {}, 消息类型: {}, 序列号范围: [{}, {}]",
                    conversationId, msgType, startSeq, endSeq, e);
        }

        return result;
    }

    /**
     * 从统一message表查询私聊消息
     */
    private List<MessageDTO> queryPrivateMessagesFromUnifiedTable(String conversationId,
                                                                 Long startSeq, Long endSeq, Integer limit) {
        List<MessageDTO> result = new ArrayList<>();

        // 1. 从user_msg_list表获取序列号范围内的消息ID
        LambdaQueryWrapper<UserMsgList> userMsgWrapper = new LambdaQueryWrapper<UserMsgList>()
                .eq(UserMsgList::getConversationId, conversationId)
                .between(UserMsgList::getSeq, startSeq, endSeq)
                .orderByAsc(UserMsgList::getSeq);

        if (limit != null && limit > 0) {
            userMsgWrapper.last("LIMIT " + limit);
        }

        List<UserMsgList> userMsgList = userMsgListMapper.selectList(userMsgWrapper);

        if (userMsgList.isEmpty()) {
            return result;
        }

        // 2. 根据消息ID从统一message表查询消息详情
        List<Long> msgIds = userMsgList.stream().map(UserMsgList::getMsgId).collect(Collectors.toList());

        LambdaQueryWrapper<Message> messageWrapper = new LambdaQueryWrapper<Message>()
                .in(Message::getMsgId, msgIds)
                .eq(Message::getMsgType, MessageTypeConstants.MSG_TYPE_PRIVATE)
                .orderByAsc(Message::getSendTime);

        List<Message> messages = messageMapper.selectList(messageWrapper);

        // 3. 转换为MessageDTO
        for (Message message : messages) {
            MessageDTO messageDTO = new MessageDTO();
            messageDTO.setType(MessageTypeConstants.PRIVATE_CHAT_MESSAGE);
            messageDTO.setContent(message.getContent());
            messageDTO.setFromId(String.valueOf(message.getSenderId()));

            // 从会话ID中解析接收方ID
            String toId = parseToIdFromConversationId(conversationId, String.valueOf(message.getSenderId()));
            messageDTO.setToId(toId);

            messageDTO.setUid(String.valueOf(message.getMsgId()));

            // 从userMsgList中找到对应的序列号
            UserMsgList userMsg = userMsgList.stream()
                    .filter(um -> um.getMsgId().equals(message.getMsgId()))
                    .findFirst().orElse(null);
            if (userMsg != null) {
                messageDTO.setSeq(String.valueOf(userMsg.getSeq()));
            }

            messageDTO.setTimestamp(message.getSendTime().getTime());
            messageDTO.setStatus(message.getStatus() != null ? message.getStatus().intValue() : null);

            result.add(messageDTO);
        }

        return result;
    }

    /**
     * 从统一message表查询群聊消息
     */
    private List<MessageDTO> queryGroupMessagesFromUnifiedTable(String conversationId,
                                                               Long startSeq, Long endSeq, Integer limit) {
        List<MessageDTO> result = new ArrayList<>();

        // 1. 从conversation_msg_list表获取序列号范围内的消息ID
        LambdaQueryWrapper<ConversationMsgList> conversationWrapper = new LambdaQueryWrapper<ConversationMsgList>()
                .eq(ConversationMsgList::getConversationId, conversationId)
                .between(ConversationMsgList::getSeq, startSeq, endSeq)
                .orderByAsc(ConversationMsgList::getSeq);

        if (limit != null && limit > 0) {
            conversationWrapper.last("LIMIT " + limit);
        }

        List<ConversationMsgList> conversationMsgList = conversationMsgListMapper.selectList(conversationWrapper);

        if (conversationMsgList.isEmpty()) {
            return result;
        }

        // 2. 根据消息ID从统一message表查询消息详情
        List<Long> msgIds = conversationMsgList.stream().map(ConversationMsgList::getMsgId).collect(Collectors.toList());

        LambdaQueryWrapper<Message> messageWrapper = new LambdaQueryWrapper<Message>()
                .in(Message::getMsgId, msgIds)
                .eq(Message::getMsgType, MessageTypeConstants.MSG_TYPE_GROUP)
                .orderByAsc(Message::getSendTime);

        List<Message> messages = messageMapper.selectList(messageWrapper);

        // 3. 转换为MessageDTO
        String groupId = conversationId.replace("group_", "");

        for (Message message : messages) {
            MessageDTO messageDTO = new MessageDTO();
            messageDTO.setType(MessageTypeConstants.GROUP_CHAT_MESSAGE);
            messageDTO.setContent(message.getContent());
            messageDTO.setFromId(String.valueOf(message.getSenderId()));
            messageDTO.setToId(groupId); // 接收方为群组ID
            messageDTO.setUid(String.valueOf(message.getMsgId()));

            // 从conversationMsgList中找到对应的序列号
            ConversationMsgList conversationMsg = conversationMsgList.stream()
                    .filter(cm -> cm.getMsgId().equals(message.getMsgId()))
                    .findFirst().orElse(null);
            if (conversationMsg != null) {
                messageDTO.setSeq(String.valueOf(conversationMsg.getSeq()));
            }

            messageDTO.setTimestamp(message.getSendTime().getTime());
            messageDTO.setStatus(message.getStatus() != null ? message.getStatus().intValue() : null);

            result.add(messageDTO);
        }

        return result;
    }

    /**
     * 从私聊会话ID中解析接收方ID
     * 私聊会话ID格式通常为：较小ID_较大ID
     *
     * @param conversationId 会话ID
     * @param senderId 发送方ID
     * @return 接收方ID
     */
    private String parseToIdFromConversationId(String conversationId, String senderId) {
        if (conversationId == null || senderId == null) {
            return null;
        }

        // 会话ID格式：user1_user2 (按ID大小排序)
        String[] parts = conversationId.split("_");
        if (parts.length != 2) {
            return null;
        }

        // 返回不是发送方的那个ID
        if (senderId.equals(parts[0])) {
            return parts[1];
        } else if (senderId.equals(parts[1])) {
            return parts[0];
        }

        return null;
    }
}