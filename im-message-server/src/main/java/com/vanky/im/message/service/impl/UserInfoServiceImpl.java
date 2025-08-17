package com.vanky.im.message.service.impl;

import com.vanky.im.common.model.ApiResponse;
import com.vanky.im.message.client.UserClient;
import com.vanky.im.message.service.UserInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 用户信息服务实现类
 * 使用 Feign 客户端调用 im-user 服务，体现 DIP 和 KISS 原则
 *
 * @author vanky
 * @since 2025-07-28
 * @updated 2025-08-14 - 重构为使用 Feign 客户端
 */
@Slf4j
@Service
public class UserInfoServiceImpl implements UserInfoService {

    @Autowired
    private UserClient userClient;
    
    @Override
    public String getUsernameById(Long userId) {
        if (userId == null) {
            return "未知用户";
        }

        try {
            log.debug("通过Feign客户端获取用户昵称 - 用户ID: {}", userId);

            // 使用 Feign 客户端调用 im-user 服务，体现 KISS 原则
            ApiResponse<String> response = userClient.getUsername(String.valueOf(userId));

            if (response != null && response.isSuccess() && response.getData() != null) {
                log.debug("成功获取用户昵称 - 用户ID: {}, 昵称: {}", userId, response.getData());
                return response.getData();
            } else {
                log.warn("获取用户昵称失败 - 用户ID: {}, 响应: {}", userId, response);
                return "用户" + userId; // 降级处理
            }

        } catch (Exception e) {
            log.error("调用用户服务失败 - 用户ID: {}", userId, e);
            return "用户" + userId; // 异常降级处理
        }
    }

}
