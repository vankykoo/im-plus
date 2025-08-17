package com.vanky.im.message.controller;


import com.vanky.im.message.model.PullMessagesRequest;
import com.vanky.im.message.model.PullMessagesResponse;
import com.vanky.im.message.model.SyncMessagesRequest;
import com.vanky.im.message.model.SyncMessagesResponse;

import com.vanky.im.message.service.OfflineMessageSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;



/**
 * 消息同步控制器
 * 提供HTTP接口供客户端进行消息同步，包括会话级同步和全局离线消息同步
 * 
 * @author vanky
 * @create 2025/7/29
 */
// {{CHENGQI:
// Action: Added; Timestamp: 2025-07-29 14:15:29 +08:00; Reason: 创建消息同步控制器，提供离线消息同步的HTTP API接口;
// }}
// {{START MODIFICATIONS}}
@Slf4j
@RestController
@RequestMapping("/api/messages")
@Validated
public class MessageSyncController {



    @Autowired
    private OfflineMessageSyncService offlineMessageSyncService;



    // ========== 离线消息同步新接口 ==========

    /**
     * 检查用户是否需要进行离线消息同步
     * 客户端在后台启动同步任务时首先调用此接口
     * 
     * @param request 同步检查请求
     * @return 同步检查响应
     */
    @PostMapping("/sync-check")
    public ResponseEntity<SyncMessagesResponse> checkSyncNeeded(@RequestBody SyncMessagesRequest request) {
        try {
            log.info("收到离线消息同步检查请求 - 用户ID: {}, 客户端同步点: {}", 
                    request.getUserId(), request.getLastSyncSeq());

            // 检查是否需要同步
            SyncMessagesResponse response = offlineMessageSyncService.checkSyncNeeded(request);

            if (response.isSuccess()) {
                log.info("离线消息同步检查完成 - 用户ID: {}, 需要同步: {}, 目标序列号: {}", 
                        request.getUserId(), response.isSyncNeeded(), response.getTargetSeq());
            } else {
                log.warn("离线消息同步检查失败 - 用户ID: {}, 错误: {}", 
                        request.getUserId(), response.getErrorMessage());
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("处理离线消息同步检查请求异常 - 用户ID: {}", request.getUserId(), e);

            SyncMessagesResponse errorResponse = SyncMessagesResponse.createErrorResponse(
                    "服务器内部错误，请稍后重试");
            return ResponseEntity.ok(errorResponse);
        }
    }

    /**
     * 批量拉取用户的未接收消息内容
     * 基于"持久化是第一原则"，直接从数据库查询用户未接收的消息
     * 客户端在确认需要同步后，分页调用此接口拉取消息
     *
     * @param request 批量拉取请求
     * @return 批量拉取响应
     */
    @PostMapping("/pull-batch")
    public ResponseEntity<PullMessagesResponse> pullMessagesBatch(@RequestBody PullMessagesRequest request) {
        try {
            log.info("收到批量拉取消息请求 - 用户ID: {}, 起始序列号: {}, 限制数量: {}",
                    request.getUserId(), request.getFromSeq(), request.getLimit());

            // 批量拉取消息
            PullMessagesResponse response = offlineMessageSyncService.pullMessagesBatch(request);

            if (response.isSuccess()) {
                log.info("批量拉取消息完成 - 用户ID: {}, 返回消息数量: {}, 是否还有更多: {}",
                        request.getUserId(), response.getCount(), response.isHasMore());
            } else {
                log.warn("批量拉取消息失败 - 用户ID: {}, 错误: {}",
                        request.getUserId(), response.getErrorMessage());
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("处理批量拉取离线消息请求异常 - 用户ID: {}", request.getUserId(), e);

            PullMessagesResponse errorResponse = PullMessagesResponse.createErrorResponse(
                    "服务器内部错误，请稍后重试");
            return ResponseEntity.ok(errorResponse);
        }
    }

    // ========== GET方式接口（便于测试） ==========

    /**
     * GET方式的离线消息同步检查接口
     * 
     * @param userId 用户ID
     * @param lastSyncSeq 客户端最后同步序列号
     * @return 同步检查响应
     */
    @GetMapping("/sync-check")
    public ResponseEntity<SyncMessagesResponse> checkSyncNeededGet(
            @RequestParam String userId,
            @RequestParam Long lastSyncSeq) {
        
        try {
            log.info("收到GET离线消息同步检查请求 - 用户ID: {}, 客户端同步点: {}", userId, lastSyncSeq);

            // 构建请求对象
            SyncMessagesRequest request = new SyncMessagesRequest(userId, lastSyncSeq);

            // 检查是否需要同步
            SyncMessagesResponse response = offlineMessageSyncService.checkSyncNeeded(request);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("处理GET离线消息同步检查请求异常 - 用户ID: {}", userId, e);

            SyncMessagesResponse errorResponse = SyncMessagesResponse.createErrorResponse(
                    "服务器内部错误，请稍后重试");
            return ResponseEntity.ok(errorResponse);
        }
    }

    /**
     * GET方式的批量拉取消息接口
     * 基于"持久化是第一原则"，直接从数据库查询用户未接收的消息
     *
     * @param userId 用户ID
     * @param fromSeq 起始序列号
     * @param limit 限制数量（可选）
     * @return 批量拉取响应
     */
    @GetMapping("/pull-batch")
    public ResponseEntity<PullMessagesResponse> pullMessagesBatchGet(
            @RequestParam String userId,
            @RequestParam Long fromSeq,
            @RequestParam(required = false, defaultValue = "200") Integer limit) {
        
        try {
            log.info("收到GET批量拉取消息请求 - 用户ID: {}, 起始序列号: {}, 限制数量: {}",
                    userId, fromSeq, limit);

            // 构建请求对象
            PullMessagesRequest request = new PullMessagesRequest(userId, fromSeq, limit);

            // 批量拉取消息
            PullMessagesResponse response = offlineMessageSyncService.pullMessagesBatch(request);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("处理GET批量拉取离线消息请求异常 - 用户ID: {}", userId, e);

            PullMessagesResponse errorResponse = PullMessagesResponse.createErrorResponse(
                    "服务器内部错误，请稍后重试");
            return ResponseEntity.ok(errorResponse);
        }
    }
}
// {{END MODIFICATIONS}}
