package com.vanky.im.message.service;

import com.vanky.im.message.model.PullMessagesRequest;
import com.vanky.im.message.model.PullMessagesResponse;
import com.vanky.im.message.model.SyncMessagesRequest;
import com.vanky.im.message.model.SyncMessagesResponse;

/**
 * 消息同步服务接口
 * 基于"持久化是第一原则"的设计理念，提供消息内容同步功能
 * 直接从数据库查询用户未接收的消息，不依赖离线消息缓存
 *
 * @author vanky
 * @create 2025/7/29
 * @modified 2025/8/15 - 重构为基于数据库的消息同步服务
 */
public interface OfflineMessageSyncService {

    /**
     * 检查用户是否需要同步消息
     * 比较客户端的全局同步点与服务端的最新序列号
     * 
     * @param request 同步检查请求
     * @return 同步检查响应，包含是否需要同步的指令
     */
    SyncMessagesResponse checkSyncNeeded(SyncMessagesRequest request);

    /**
     * 批量拉取用户的未接收消息
     * 基于数据库查询用户未接收的消息，根据起始序列号分页返回消息内容
     *
     * @param request 批量拉取请求
     * @return 批量拉取响应，包含消息列表和分页信息
     */
    PullMessagesResponse pullMessagesBatch(PullMessagesRequest request);

    /**
     * 获取用户的最大全局序列号
     * 优先从Redis缓存获取，缓存未命中时查询数据库
     * 
     * @param userId 用户ID
     * @return 用户最大全局序列号，如果用户无消息则返回0
     */
    Long getUserMaxGlobalSeq(String userId);

    /**
     * 获取用户在指定序列号范围内的消息数量
     * 用于估算同步进度
     * 
     * @param userId 用户ID
     * @param fromSeq 起始序列号
     * @param toSeq 结束序列号
     * @return 消息数量
     */
    Long getMessageCountInRange(String userId, Long fromSeq, Long toSeq);
}
// {{END MODIFICATIONS}}
