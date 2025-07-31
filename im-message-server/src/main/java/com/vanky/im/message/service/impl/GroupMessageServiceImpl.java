package com.vanky.im.message.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.vanky.im.message.constants.MessageTypeConstants;
import com.vanky.im.message.entity.GroupMessage;
import com.vanky.im.message.entity.Message;
import com.vanky.im.message.mapper.GroupMessageMapper;
import com.vanky.im.message.service.GroupMessageService;
import com.vanky.im.message.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
* @author vanky
* @description 针对表【group_message】的数据库操作Service实现
* @createDate 2025-06-06
*/
@Service
public class GroupMessageServiceImpl extends ServiceImpl<GroupMessageMapper, GroupMessage>
    implements GroupMessageService{

    @Autowired
    private MessageService messageService;

    @Override
    public GroupMessage getByMsgId(String msgId) {
        // 为了保持兼容性，从统一的message表查询群聊消息，然后转换为GroupMessage
        Message message = messageService.getByMsgId(msgId);
        if (message != null && MessageTypeConstants.isGroupMessage(message.getMsgType())) {
            return convertToGroupMessage(message);
        }
        return null;
    }

    /**
     * 将Message转换为GroupMessage（兼容性方法）
     */
    private GroupMessage convertToGroupMessage(Message message) {
        GroupMessage groupMessage = new GroupMessage();
        groupMessage.setId(message.getId());
        groupMessage.setMsgId(message.getMsgId());
        groupMessage.setConversationId(message.getConversationId());
        groupMessage.setUserId(message.getSenderId()); // 注意字段名映射
        groupMessage.setContent(message.getContent());
        groupMessage.setStatus(message.getStatus() != null ? message.getStatus().intValue() : null);
        groupMessage.setSendTime(message.getSendTime());
        return groupMessage;
    }
}