package com.vanky.im.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.vanky.im.message.entity.Message;
import com.vanky.im.message.mapper.MessageMapper;
import com.vanky.im.message.service.MessageService;
import com.vanky.im.message.mapper.UserMsgListMapper;
import com.vanky.im.message.entity.UserMsgList;
import com.vanky.im.message.mapper.ConversationMsgListMapper;
import com.vanky.im.message.entity.ConversationMsgList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.stream.Collectors;

import java.util.List;

/**
 * 统一消息服务实现类
 * 
 * @author vanky
 * @description 针对表【message】的数据库操作Service实现
 * @createDate 2025-07-28
 */
// {{CHENGQI:
// Action: Added; Timestamp: 2025-07-28 22:32:26 +08:00; Reason: 创建MessageServiceImpl实现类，提供统一的消息服务实现;
// }}
// {{START MODIFICATIONS}}
@Slf4j
@Service
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message>
    implements MessageService {

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private UserMsgListMapper userMsgListMapper;

    @Autowired
    private ConversationMsgListMapper conversationMsgListMapper;

    @Override
    public Message getByMsgId(String msgId) {
        if (msgId == null) {
            return null;
        }
        try {
            Long msgIdLong = Long.valueOf(msgId);
            return getByMsgId(msgIdLong);
        } catch (NumberFormatException e) {
            log.warn("消息ID格式错误: {}", msgId);
            return null;
        }
    }

    @Override
    public Message getByMsgId(Long msgId) {
        if (msgId == null) {
            return null;
        }
        return this.lambdaQuery()
                .eq(Message::getMsgId, msgId)
                .one();
    }

    @Override
    public List<Message> getByConversationIdAndType(String conversationId, Integer msgType, Integer limit) {
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, conversationId)
                .eq(Message::getMsgType, msgType)
                .orderByDesc(Message::getSendTime);
        
        if (limit != null && limit > 0) {
            wrapper.last("LIMIT " + limit);
        }
        
        return this.list(wrapper);
    }

    @Override
    public List<Message> getByConversationIdAndTypeAndSeqRange(String conversationId, Integer msgType, 
                                                              Long startSeq, Long endSeq, Integer limit) {
        // 注意：这里需要结合user_msg_list或conversation_msg_list表来查询序列号范围
        // 暂时先按发送时间排序，后续可以优化
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, conversationId)
                .eq(Message::getMsgType, msgType)
                .orderByAsc(Message::getSendTime);
        
        if (limit != null && limit > 0) {
            wrapper.last("LIMIT " + limit);
        }
        
        return this.list(wrapper);
    }

    @Override
    public boolean updateMessageStatus(String msgId, Integer status) {
        if (msgId == null || status == null) {
            return false;
        }
        try {
            Long msgIdLong = Long.valueOf(msgId);
            return updateMessageStatus(msgIdLong, status);
        } catch (NumberFormatException e) {
            log.warn("消息ID格式错误: {}", msgId);
            return false;
        }
    }

    @Override
    public boolean updateMessageStatus(Long msgId, Integer status) {
        if (msgId == null || status == null) {
            return false;
        }
        
        LambdaUpdateWrapper<Message> wrapper = new LambdaUpdateWrapper<Message>()
                .eq(Message::getMsgId, msgId)
                .set(Message::getStatus, status);
        
        return this.update(wrapper);
    }

    @Override
    public List<Message> getBySenderIdAndType(Long senderId, Integer msgType, Integer limit) {
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<Message>()
                .eq(Message::getSenderId, senderId)
                .eq(Message::getMsgType, msgType)
                .orderByDesc(Message::getSendTime);

        if (limit != null && limit > 0) {
            wrapper.last("LIMIT " + limit);
        }

        return this.list(wrapper);
    }

    @Override
    public List<Message> listByMsgIds(List<Long> msgIds) {
        if (msgIds == null || msgIds.isEmpty()) {
            return List.of();
        }

        return this.lambdaQuery()
                .in(Message::getMsgId, msgIds)
                .list();
    }

    // ========== 新增方法实现：消息已读功能支持 ==========

    @Override
    public int updateMessagesReadStatus(String conversationId, String receiverId, long startSeq, long endSeq) {
        if (conversationId == null || receiverId == null || startSeq > endSeq) {
            log.warn("参数无效 - 会话: {}, 接收方: {}, 序列号范围: [{}, {}]",
                    conversationId, receiverId, startSeq, endSeq);
            return 0;
        }

        try {
            // 1. 通过user_msg_list表查找用户在指定序列号范围内接收的消息ID
            LambdaQueryWrapper<UserMsgList> userMsgWrapper = new LambdaQueryWrapper<UserMsgList>()
                    .eq(UserMsgList::getUserId, Long.valueOf(receiverId))
                    .eq(UserMsgList::getConversationId, conversationId)
                    .ge(UserMsgList::getSeq, startSeq)
                    .le(UserMsgList::getSeq, endSeq);

            List<UserMsgList> userMsgList = userMsgListMapper.selectList(userMsgWrapper);

            if (userMsgList.isEmpty()) {
                log.debug("指定序列号范围内没有用户消息记录 - 会话: {}, 接收方: {}, 序列号范围: [{}, {}]",
                        conversationId, receiverId, startSeq, endSeq);
                return 0;
            }

            // 2. 提取消息ID列表
            List<Long> msgIds = userMsgList.stream()
                    .map(UserMsgList::getMsgId)
                    .collect(Collectors.toList());

            // 3. 批量更新这些消息的状态为已读，但只更新不是用户自己发送的消息
            LambdaUpdateWrapper<Message> messageWrapper = new LambdaUpdateWrapper<Message>()
                    .in(Message::getMsgId, msgIds)
                    .ne(Message::getSenderId, Long.valueOf(receiverId)) // 不更新用户自己发送的消息
                    .set(Message::getStatus, 2); // 设置为已读状态

            int updatedCount = this.getBaseMapper().update(null, messageWrapper);

            log.debug("更新私聊消息已读状态 - 会话: {}, 接收方: {}, 序列号范围: [{}, {}], 消息数量: {}, 更新数量: {}",
                    conversationId, receiverId, startSeq, endSeq, msgIds.size(), updatedCount);

            return updatedCount;

        } catch (Exception e) {
            log.error("更新私聊消息已读状态失败 - 会话: {}, 接收方: {}, 序列号范围: [{}, {}]",
                    conversationId, receiverId, startSeq, endSeq, e);
            return 0;
        }
    }

    @Override
    public List<String> getGroupMessageIdsBySeqRange(String conversationId, long startSeq, long endSeq) {
        if (conversationId == null || startSeq > endSeq) {
            log.warn("参数无效 - 会话: {}, 序列号范围: [{}, {}]", conversationId, startSeq, endSeq);
            return List.of();
        }

        try {
            // 1. 通过conversation_msg_list表查找指定序列号范围内的消息ID
            LambdaQueryWrapper<ConversationMsgList> wrapper = new LambdaQueryWrapper<ConversationMsgList>()
                    .eq(ConversationMsgList::getConversationId, conversationId)
                    .ge(ConversationMsgList::getSeq, startSeq)
                    .le(ConversationMsgList::getSeq, endSeq)
                    .orderByAsc(ConversationMsgList::getSeq);

            List<ConversationMsgList> conversationMsgList = conversationMsgListMapper.selectList(wrapper);

            if (conversationMsgList.isEmpty()) {
                log.debug("指定序列号范围内没有群聊消息 - 会话: {}, 序列号范围: [{}, {}]",
                        conversationId, startSeq, endSeq);
                return List.of();
            }

            // 2. 提取消息ID并转换为字符串列表
            List<String> messageIds = conversationMsgList.stream()
                    .map(item -> String.valueOf(item.getMsgId()))
                    .collect(Collectors.toList());

            log.debug("获取群聊消息ID列表 - 会话: {}, 序列号范围: [{}, {}], 消息数量: {}",
                    conversationId, startSeq, endSeq, messageIds.size());

            return messageIds;

        } catch (Exception e) {
            log.error("获取群聊消息ID列表失败 - 会话: {}, 序列号范围: [{}, {}]",
                    conversationId, startSeq, endSeq, e);
            return List.of();
        }
    }

    @Override
    public Map<String, String> getMessageSenders(List<String> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return Map.of();
        }

        try {
            // 转换为Long类型的ID列表
            List<Long> longIds = messageIds.stream()
                    .map(Long::valueOf)
                    .collect(Collectors.toList());

            // 查询消息列表
            List<Message> messages = this.lambdaQuery()
                    .in(Message::getMsgId, longIds)
                    .select(Message::getMsgId, Message::getSenderId)
                    .list();

            // 构建消息ID -> 发送方ID的映射
            Map<String, String> senderMap = messages.stream()
                    .collect(Collectors.toMap(
                            msg -> String.valueOf(msg.getMsgId()),
                            msg -> String.valueOf(msg.getSenderId()),
                            (existing, replacement) -> existing // 处理重复key
                    ));

            log.debug("获取消息发送方信息 - 消息数量: {}, 映射数量: {}", messageIds.size(), senderMap.size());
            return senderMap;

        } catch (Exception e) {
            log.error("获取消息发送方信息失败 - 消息ID列表: {}", messageIds, e);
            return Map.of();
        }
    }

    @Override
    public String getMessageConversationId(String msgId) {
        if (msgId == null || msgId.trim().isEmpty()) {
            return null;
        }

        try {
            Long msgIdLong = Long.valueOf(msgId);
            Message message = this.lambdaQuery()
                    .eq(Message::getMsgId, msgIdLong)
                    .select(Message::getConversationId)
                    .one();

            if (message != null) {
                log.debug("获取消息会话ID - 消息ID: {}, 会话ID: {}", msgId, message.getConversationId());
                return message.getConversationId();
            }

            log.debug("消息不存在 - 消息ID: {}", msgId);
            return null;

        } catch (NumberFormatException e) {
            log.error("消息ID格式错误 - 消息ID: {}", msgId, e);
            return null;
        } catch (Exception e) {
            log.error("获取消息会话ID失败 - 消息ID: {}", msgId, e);
            return null;
        }
    }
}
// {{END MODIFICATIONS}}
