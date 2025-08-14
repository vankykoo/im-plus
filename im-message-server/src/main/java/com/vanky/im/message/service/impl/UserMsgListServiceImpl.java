package com.vanky.im.message.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.vanky.im.message.entity.UserMsgList;
import com.vanky.im.message.mapper.UserMsgListMapper;
import com.vanky.im.message.service.RedisService;
import com.vanky.im.message.service.UserMsgListService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
* @author vanky
* @description 针对表【user_msg_list】的数据库操作Service实现
* @createDate 2025-06-06
*/
@Slf4j
@Service
public class UserMsgListServiceImpl extends ServiceImpl<UserMsgListMapper, UserMsgList>
    implements UserMsgListService {

    @Autowired
    private RedisService redisService;

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

    @Override
    public Long saveUserMessageRecord(String userId, String msgId, String conversationId) {
        // {{CHENGQI:
        // Action: Added; Timestamp: 2025-07-28 23:08:31 +08:00; Reason: 实现用户级全局seq生成和消息记录插入;
        // }}
        // {{START MODIFICATIONS}}
        try {
            // 1. 生成用户级全局seq
            Long userGlobalSeq = redisService.generateUserGlobalSeq(userId);

            // 2. 插入用户消息记录
            Date now = new Date();
            UserMsgList userMsgRecord = new UserMsgList();
            userMsgRecord.setUserId(Long.valueOf(userId));
            userMsgRecord.setMsgId(Long.valueOf(msgId)); // 直接使用雪花算法生成的ID
            userMsgRecord.setConversationId(conversationId);
            userMsgRecord.setSeq(userGlobalSeq); // 使用用户级全局seq
            userMsgRecord.setCreateTime(now);

            this.save(userMsgRecord);

            log.debug("保存用户消息记录完成 - 用户ID: " + userId + ", 消息ID: " + msgId + ", 用户全局Seq: " + userGlobalSeq);

            return userGlobalSeq;

        } catch (Exception e) {
            log.error("保存用户消息记录失败 - 用户ID: " + userId + ", 消息ID: " + msgId, e);
            throw new RuntimeException("保存用户消息记录失败", e);
        }
        // {{END MODIFICATIONS}}
    }

    @Override
    public Long getMaxSeqByUserId(String userId) {
        try {
            Long maxSeq = baseMapper.selectMaxSeqByUserId(userId);
            return maxSeq != null ? maxSeq : 0L;
        } catch (Exception e) {
            log.error("查询用户最大序列号失败 - 用户ID: {}", userId, e);
            return 0L;
        }
    }
}