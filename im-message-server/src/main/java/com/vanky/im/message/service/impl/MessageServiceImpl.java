package com.vanky.im.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.vanky.im.message.entity.Message;
import com.vanky.im.message.mapper.MessageMapper;
import com.vanky.im.message.service.MessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
}
// {{END MODIFICATIONS}}
