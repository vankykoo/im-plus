package com.vanky.im.server.service.impl;

import com.vanky.im.server.service.ConversationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 会话服务实现类
 * @author vanky
 * @date 2025-06-08
 */
@Service
@Slf4j
public class ConversationServiceImpl implements ConversationService {

    private static final String GATEWAY_CONVERSATION_API = "http://localhost:8900/api/conversation";
    
    @Autowired
    private RestTemplate restTemplate;
    
    /**
     * 更新会话最后消息时间
     * Gateway模块是会话主体逻辑的所有者，因此通过HTTP API调用Gateway的会话更新接口
     * @param conversationId 会话ID
     * @param timestamp 消息时间戳
     * @return 是否更新成功
     */
    @Override
    public boolean updateLastMsgTime(String conversationId, long timestamp) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("conversationId", conversationId);
            params.put("lastMsgTime", new Date(timestamp));
            
            restTemplate.postForEntity(GATEWAY_CONVERSATION_API + "/updateLastMsgTime", params, Void.class);
            return true;
        } catch (Exception e) {
            log.error("更新会话最后消息时间失败 - 会话ID: {}, 错误: {}", conversationId, e.getMessage(), e);
            return false;
        }
    }
} 