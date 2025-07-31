package com.vanky.im.gateway.task;

import com.vanky.im.gateway.service.UserOfflineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 在线用户清理定时任务
 * 定期清理Redis中过期的在线用户状态，防止僵尸状态积累
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "im.gateway.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class OnlineUserCleanupTask {

    @Autowired
    private UserOfflineService userOfflineService;

    /**
     * 定期清理过期的在线用户状态
     * 每5分钟执行一次
     */
    @Scheduled(fixedRate = 5 * 60 * 1000) // 5分钟
    public void cleanupExpiredOnlineUsers() {
        try {
            log.debug("开始执行在线用户清理任务");
            userOfflineService.cleanupExpiredOnlineUsers();
            log.debug("在线用户清理任务执行完成");
        } catch (Exception e) {
            log.error("在线用户清理任务执行失败", e);
        }
    }
}
