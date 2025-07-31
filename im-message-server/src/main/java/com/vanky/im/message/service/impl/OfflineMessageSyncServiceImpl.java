package com.vanky.im.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vanky.im.message.entity.Message;
import com.vanky.im.message.entity.UserMsgList;
import com.vanky.im.message.mapper.UserMsgListMapper;
import com.vanky.im.message.model.MessageInfo;
import com.vanky.im.message.model.PullMessagesRequest;
import com.vanky.im.message.model.PullMessagesResponse;
import com.vanky.im.message.model.SyncMessagesRequest;
import com.vanky.im.message.model.SyncMessagesResponse;
import com.vanky.im.message.service.MessageService;
import com.vanky.im.message.service.OfflineMessageSyncService;
import com.vanky.im.message.service.RedisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 离线消息同步服务实现类
 * 
 * @author vanky
 * @create 2025/7/29
 */
// {{CHENGQI:
// Action: Added; Timestamp: 2025-07-29 14:15:29 +08:00; Reason: 实现离线消息同步服务，提供消息内容同步的核心功能;
// }}
// {{START MODIFICATIONS}}
@Service
public class OfflineMessageSyncServiceImpl implements OfflineMessageSyncService {

    private static final Logger log = LoggerFactory.getLogger(OfflineMessageSyncServiceImpl.class);

    @Autowired
    private RedisService redisService;

    @Autowired
    private UserMsgListMapper userMsgListMapper;

    @Autowired
    private MessageService messageService;

    @Override
    public SyncMessagesResponse checkSyncNeeded(SyncMessagesRequest request) {
        try {
            log.info("检查用户是否需要同步消息 - 用户ID: {}, 客户端同步点: {}", 
                    request.getUserId(), request.getLastSyncSeq());

            // 1. 获取服务端当前最新序列号
            Long serverMaxSeq = getUserMaxGlobalSeq(request.getUserId());
            
            // 2. 比较客户端和服务端序列号
            if (serverMaxSeq <= request.getLastSyncSeq()) {
                // 无需同步
                log.info("无需同步消息 - 用户ID: {}, 服务端序列号: {}, 客户端序列号: {}", 
                        request.getUserId(), serverMaxSeq, request.getLastSyncSeq());
                return SyncMessagesResponse.createNoSyncResponse(serverMaxSeq, request.getLastSyncSeq());
            }

            // 需要同步
            log.info("需要同步消息 - 用户ID: {}, 服务端序列号: {}, 客户端序列号: {}", 
                    request.getUserId(), serverMaxSeq, request.getLastSyncSeq());
            return SyncMessagesResponse.createSyncNeededResponse(serverMaxSeq, request.getLastSyncSeq());

        } catch (Exception e) {
            log.error("检查用户是否需要同步消息异常 - {}", request, e);
            return SyncMessagesResponse.createErrorResponse("服务器内部错误，请稍后重试");
        }
    }

    @Override
    public PullMessagesResponse pullMessagesBatch(PullMessagesRequest request) {
        try {
            log.info("批量拉取用户离线消息 - 用户ID: {}, 起始序列号: {}, 限制数量: {}", 
                    request.getUserId(), request.getFromSeq(), request.getLimit());

            // 1. 参数校验
            if (!StringUtils.hasText(request.getUserId()) || request.getFromSeq() == null || request.getFromSeq() <= 0) {
                return PullMessagesResponse.createErrorResponse("请求参数无效");
            }

            // 2. 查询用户消息记录（只查询未推送的消息，避免重复拉取）
            List<UserMsgList> userMsgRecords = userMsgListMapper.selectUndeliveredByUserIdAndSeqRange(
                    request.getUserId(), request.getFromSeq(), request.getLimit());

            if (CollectionUtils.isEmpty(userMsgRecords)) {
                log.info("用户无离线消息 - 用户ID: {}, 起始序列号: {}", 
                        request.getUserId(), request.getFromSeq());
                return PullMessagesResponse.createEmptyResponse(request.getFromSeq());
            }

            // 3. 批量查询消息内容
            List<Long> msgIds = userMsgRecords.stream()
                    .map(UserMsgList::getMsgId)
                    .collect(Collectors.toList());

            List<Message> messages = messageService.listByMsgIds(msgIds);
            if (CollectionUtils.isEmpty(messages)) {
                log.warn("查询消息内容为空 - 用户ID: {}, 消息ID数量: {}", 
                        request.getUserId(), msgIds.size());
                return PullMessagesResponse.createEmptyResponse(request.getFromSeq());
            }

            // 4. 转换为MessageInfo并按seq排序
            List<MessageInfo> messageInfos = convertToMessageInfos(userMsgRecords, messages);

            // 5. 计算分页信息
            boolean hasMore = userMsgRecords.size() >= request.getLimit();
            Long nextSeq = hasMore ? userMsgRecords.get(userMsgRecords.size() - 1).getSeq() + 1 : null;

            log.info("批量拉取用户离线消息完成 - 用户ID: {}, 返回消息数量: {}, 是否还有更多: {}", 
                    request.getUserId(), messageInfos.size(), hasMore);

            return PullMessagesResponse.createSuccessResponse(messageInfos, hasMore, nextSeq);

        } catch (Exception e) {
            log.error("批量拉取用户离线消息异常 - {}", request, e);
            return PullMessagesResponse.createErrorResponse("服务器内部错误，请稍后重试");
        }
    }

    @Override
    public Long getUserMaxGlobalSeq(String userId) {
        try {
            // 1. 优先从Redis缓存获取
            Long cachedMaxSeq = redisService.getUserMaxGlobalSeq(userId);
            if (cachedMaxSeq != null && cachedMaxSeq > 0) {
                log.debug("从缓存获取用户最大全局序列号 - 用户ID: {}, 最大序列号: {}", userId, cachedMaxSeq);
                return cachedMaxSeq;
            }

            // 2. 缓存未命中，从数据库查询
            Long dbMaxSeq = userMsgListMapper.selectMaxSeqByUserId(userId);
            Long result = dbMaxSeq != null ? dbMaxSeq : 0L;

            log.debug("从数据库获取用户最大全局序列号 - 用户ID: {}, 最大序列号: {}", userId, result);
            return result;

        } catch (Exception e) {
            log.error("获取用户最大全局序列号失败 - 用户ID: {}", userId, e);
            return 0L;
        }
    }

    @Override
    public Long getMessageCountInRange(String userId, Long fromSeq, Long toSeq) {
        try {
            QueryWrapper<UserMsgList> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("user_id", userId)
                       .ge("seq", fromSeq)
                       .le("seq", toSeq);

            Long count = userMsgListMapper.selectCount(queryWrapper);
            log.debug("获取用户序列号范围内消息数量 - 用户ID: {}, 范围: [{}, {}], 数量: {}", 
                    userId, fromSeq, toSeq, count);
            return count;

        } catch (Exception e) {
            log.error("获取用户序列号范围内消息数量失败 - 用户ID: {}, 范围: [{}, {}]", 
                    userId, fromSeq, toSeq, e);
            return 0L;
        }
    }

    /**
     * 将UserMsgList和Message转换为MessageInfo
     */
    private List<MessageInfo> convertToMessageInfos(List<UserMsgList> userMsgRecords, List<Message> messages) {
        // 创建消息ID到Message的映射
        var messageMap = messages.stream()
                .collect(Collectors.toMap(msg -> msg.getMsgId().toString(), msg -> msg));

        List<MessageInfo> messageInfos = new ArrayList<>();
        
        for (UserMsgList userMsgRecord : userMsgRecords) {
            String msgId = userMsgRecord.getMsgId().toString();
            Message message = messageMap.get(msgId);
            
            if (message != null) {
                MessageInfo messageInfo = new MessageInfo();
                messageInfo.setMsgId(msgId);
                messageInfo.setSeq(userMsgRecord.getSeq());
                messageInfo.setConversationId(userMsgRecord.getConversationId());
                messageInfo.setFromUserId(message.getSenderId().toString());
                messageInfo.setMsgType(message.getMsgType());
                messageInfo.setContentType(message.getContentType());
                messageInfo.setContent(message.getContent());
                messageInfo.setStatus(message.getStatus());
                messageInfo.setCreateTime(message.getSendTime());
                messageInfo.setUpdateTime(message.getUpdateTime());

                messageInfos.add(messageInfo);
            }
        }

        // 按seq排序
        messageInfos.sort((a, b) -> Long.compare(a.getSeq(), b.getSeq()));
        
        return messageInfos;
    }
}
// {{END MODIFICATIONS}}
