package com.vanky.im.message.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.vanky.im.message.entity.UserMsgList;
import com.vanky.im.message.mapper.UserMsgListMapper;
import com.vanky.im.message.service.UserMsgListService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
* @author vanky
* @description 针对表【user_msg_list】的数据库操作Service实现
* @createDate 2025-06-06
*/
@Service
public class UserMsgListServiceImpl extends ServiceImpl<UserMsgListMapper, UserMsgList>
    implements UserMsgListService {

    @Override
    public void saveWriteExpandRecords(String msgId, String conversationId, Long seq, String fromUserId, String toUserId) {
        Date now = new Date();
        List<UserMsgList> records = new ArrayList<>();
        
        // 发送方记录
        UserMsgList senderRecord = new UserMsgList();
        senderRecord.setUserId(Long.valueOf(fromUserId));
        senderRecord.setMsgId(Long.valueOf(msgId.hashCode()));
        senderRecord.setConversationId(conversationId);
        senderRecord.setSeq(seq);
        senderRecord.setCreateTime(now);
        records.add(senderRecord);
        
        // 接收方记录
        UserMsgList receiverRecord = new UserMsgList();
        receiverRecord.setUserId(Long.valueOf(toUserId));
        receiverRecord.setMsgId(Long.valueOf(msgId.hashCode()));
        receiverRecord.setConversationId(conversationId);
        receiverRecord.setSeq(seq);
        receiverRecord.setCreateTime(now);
        records.add(receiverRecord);
        
        // 批量保存
        this.saveBatch(records);
    }
}