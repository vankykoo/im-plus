package com.vanky.im.gateway.server.processor.client;

import com.vanky.im.common.protocol.ChatMessage;
import lombok.extern.slf4j.Slf4j;

/**
 * @author vanky
 * @create 2025/5/22 21:07
 * @description 群组消息处理器
 */
@Slf4j
public class GroupMsgProcessor {
    public static void process(ChatMessage processedMsg) {
        log.info("群组消息处理器处理群组消息: {}", processedMsg);
    }
}
