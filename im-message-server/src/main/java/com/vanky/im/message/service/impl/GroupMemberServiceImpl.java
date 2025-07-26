package com.vanky.im.message.service.impl;

import com.vanky.im.common.constant.SessionConstants;
import com.vanky.im.message.service.GroupMemberService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 群组成员服务实现类
 * 负责群组成员管理
 */
@Slf4j
@Service
public class GroupMemberServiceImpl implements GroupMemberService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    // 群组成员缓存前缀
    private static final String GROUP_MEMBERS_PREFIX = "group:members:";
    
    @Override
    public List<String> getGroupMemberIds(String groupId) {
        try {
            String key = GROUP_MEMBERS_PREFIX + groupId;
            Set<Object> members = redisTemplate.opsForSet().members(key);
            
            if (members == null || members.isEmpty()) {
                log.warn("群组成员列表为空 - 群组ID: {}", groupId);
                return new ArrayList<>();
            }
            
            return members.stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("获取群组成员失败 - 群组ID: {}", groupId, e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public boolean isGroupMember(String groupId, String userId) {
        try {
            String key = GROUP_MEMBERS_PREFIX + groupId;
            return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key, userId));
        } catch (Exception e) {
            log.error("检查群组成员身份失败 - 群组ID: {}, 用户ID: {}", groupId, userId, e);
            return false;
        }
    }
    
    @Override
    public Set<String> getOnlineGroupMembers(String groupId) {
        try {
            // 1. 获取群组所有成员
            List<String> allMembers = getGroupMemberIds(groupId);
            
            if (allMembers.isEmpty()) {
                return new HashSet<>();
            }
            
            // 2. 检查哪些成员在线
            Set<String> onlineMembers = new HashSet<>();
            for (String memberId : allMembers) {
                String userSessionKey = SessionConstants.getUserSessionKey(memberId);
                if (Boolean.TRUE.equals(redisTemplate.hasKey(userSessionKey))) {
                    onlineMembers.add(memberId);
                }
            }
            
            log.debug("群组在线成员统计 - 群组ID: {}, 总成员数: {}, 在线成员数: {}", 
                    groupId, allMembers.size(), onlineMembers.size());
            
            return onlineMembers;
        } catch (Exception e) {
            log.error("获取群组在线成员失败 - 群组ID: {}", groupId, e);
            return new HashSet<>();
        }
    }
    
    /**
     * 添加群组成员（管理方法）
     * @param groupId 群组ID
     * @param userId 用户ID
     */
    public void addGroupMember(String groupId, String userId) {
        try {
            String key = GROUP_MEMBERS_PREFIX + groupId;
            redisTemplate.opsForSet().add(key, userId);
            log.info("添加群组成员 - 群组ID: {}, 用户ID: {}", groupId, userId);
        } catch (Exception e) {
            log.error("添加群组成员失败 - 群组ID: {}, 用户ID: {}", groupId, userId, e);
        }
    }
    
    /**
     * 移除群组成员（管理方法）
     * @param groupId 群组ID
     * @param userId 用户ID
     */
    public void removeGroupMember(String groupId, String userId) {
        try {
            String key = GROUP_MEMBERS_PREFIX + groupId;
            redisTemplate.opsForSet().remove(key, userId);
            log.info("移除群组成员 - 群组ID: {}, 用户ID: {}", groupId, userId);
        } catch (Exception e) {
            log.error("移除群组成员失败 - 群组ID: {}, 用户ID: {}", groupId, userId, e);
        }
    }
    
    /**
     * 获取群组成员数量
     * @param groupId 群组ID
     * @return 成员数量
     */
    public long getGroupMemberCount(String groupId) {
        try {
            String key = GROUP_MEMBERS_PREFIX + groupId;
            Long count = redisTemplate.opsForSet().size(key);
            return count != null ? count : 0L;
        } catch (Exception e) {
            log.error("获取群组成员数量失败 - 群组ID: {}", groupId, e);
            return 0L;
        }
    }
}