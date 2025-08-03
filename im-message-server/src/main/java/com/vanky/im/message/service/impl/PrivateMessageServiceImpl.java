package com.vanky.im.message.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.vanky.im.common.constant.MessageTypeConstants;
import com.vanky.im.message.entity.Message;
import com.vanky.im.message.entity.PrivateMessage;
import com.vanky.im.message.mapper.PrivateMessageMapper;
import com.vanky.im.message.service.MessageService;
import com.vanky.im.message.service.PrivateMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
* @author vanky
* @description 针对表【private_message】的数据库操作Service实现
* @createDate 2025-06-06
*/
@Service
public class PrivateMessageServiceImpl extends ServiceImpl<PrivateMessageMapper, PrivateMessage>
    implements PrivateMessageService{

    @Autowired
    private MessageService messageService;

    @Override
    public PrivateMessage getByMsgId(String msgId) {
        // 为了保持兼容性，从统一的message表查询私聊消息，然后转换为PrivateMessage
        Message message = messageService.getByMsgId(msgId);
        if (message != null && MessageTypeConstants.isPrivateMessage(message.getMsgType())) {
            return convertToPrivateMessage(message);
        }
        return null;
    }

    /**
     * 将Message转换为PrivateMessage（兼容性方法）
     */
    private PrivateMessage convertToPrivateMessage(Message message) {
        PrivateMessage privateMessage = new PrivateMessage();
        privateMessage.setId(message.getId());
        privateMessage.setMsgId(message.getMsgId());
        privateMessage.setConversationId(message.getConversationId());
        privateMessage.setUserId(message.getSenderId()); // 注意字段名映射
        privateMessage.setContent(message.getContent());
        privateMessage.setStatus(message.getStatus() != null ? message.getStatus().intValue() : null);
        privateMessage.setSendTime(message.getSendTime());
        return privateMessage;
    }
}