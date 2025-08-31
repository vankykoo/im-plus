package com.vanky.im.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vanky.im.common.constant.RedisKeyConstants;
import com.vanky.im.common.constant.SessionConstants;
import com.vanky.im.common.util.CacheSafetyManager;
import com.vanky.im.message.entity.UserConversationList;
import com.vanky.im.message.mapper.UserConversationListMapper;
import com.vanky.im.message.service.GroupMemberService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 群组成员服务实现类
 * 负责群组成员管理
 *
 * 更新记录 (2025-08-04 11:25:13 +08:00):
 * - 修复群组成员判断逻辑：Redis缓存 + 数据库兜底机制
 * - 新增从user_conversation_list表查询群组成员的数据库兜底方案
 * - 优化缓存策略：数据库查询成功后自动回填Redis缓存
 * - 支持群组ID与会话ID的格式转换（group_前缀处理）
 */
@Slf4j
@Service
public class GroupMemberServiceImpl implements GroupMemberService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private UserConversationListMapper userConversationListMapper;

    @Autowired
    private CacheSafetyManager cacheSafetyManager;

    // 注意：Redis key前缀和缓存配置已迁移到RedisKeyConstants类
    
    @Override
    public List<String> getGroupMemberIds(String groupId) {
        String cacheKey = RedisKeyConstants.getGroupMembersKey(groupId);
        
        // 使用缓存安全管理器，但由于需要特殊处理Set类型，这里保持原有逻辑并增强
        return safeGetGroupMemberList(cacheKey, () -> getGroupMemberIdsFromDb(groupId));
    }

    /**
     * 安全获取群组成员列表，带缓存穿透和缓存击穿保护
     * 由于群组成员使用Set类型存储，需要特殊处理
     */
    private List<String> safeGetGroupMemberList(String cacheKey, java.util.function.Supplier<List<String>> dataLoader) {
        try {
            // 1. 检查缓存是否存在
            Boolean exists = redisTemplate.hasKey(cacheKey);
            if (Boolean.TRUE.equals(exists)) {
                Set<Object> members = redisTemplate.opsForSet().members(cacheKey);
                if (members != null && !members.isEmpty()) {
                    log.debug("从Redis缓存获取群组成员 - key: {}, 成员数: {}", cacheKey, members.size());
                    return members.stream()
                            .map(Object::toString)
                            .collect(Collectors.toList());
                }
            }

            // 2. 缓存未命中，使用分布式锁防止缓存击穿
            String lockKey = "cache:lock:" + cacheKey;
            String lockValue = String.valueOf(System.currentTimeMillis());
            
            Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(
                    lockKey, lockValue, java.time.Duration.ofSeconds(30));
            
            if (Boolean.TRUE.equals(lockAcquired)) {
                try {
                    // 双重检查缓存
                    exists = redisTemplate.hasKey(cacheKey);
                    if (Boolean.TRUE.equals(exists)) {
                        Set<Object> members = redisTemplate.opsForSet().members(cacheKey);
                        if (members != null && !members.isEmpty()) {
                            return members.stream()
                                    .map(Object::toString)
                                    .collect(Collectors.toList());
                        }
                    }
                    
                    // 从数据库加载
                    log.debug("从数据库查询群组成员 - key: {}", cacheKey);
                    List<String> memberIds = dataLoader.get();
                    
                    if (memberIds != null && !memberIds.isEmpty()) {
                        // 缓存正常数据
                        String[] memberArray = memberIds.toArray(new String[0]);
                        redisTemplate.opsForSet().add(cacheKey, (Object[]) memberArray);
                        redisTemplate.expire(cacheKey, RedisKeyConstants.CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
                        log.debug("缓存群组成员成功 - key: {}, 成员数: {}", cacheKey, memberIds.size());
                        return memberIds;
                    } else {
                        // 缓存空值标记
                        cacheSafetyManager.setNullValueCache(cacheKey);
                        log.debug("缓存群组空值标记 - key: {}", cacheKey);
                        return new ArrayList<>();
                    }
                    
                } finally {
                    // 释放锁
                    String luaScript = 
                        "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
                        "    return redis.call('DEL', KEYS[1]) " +
                        "else " +
                        "    return 0 " +
                        "end";
                    redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                        return connection.eval(luaScript.getBytes(), 
                                             org.springframework.data.redis.connection.ReturnType.INTEGER, 
                                             1, lockKey.getBytes(), lockValue.getBytes());
                    });
                }
            } else {
                // 获取锁失败，等待后重试
                for (int i = 0; i < 3; i++) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    
                    exists = redisTemplate.hasKey(cacheKey);
                    if (Boolean.TRUE.equals(exists)) {
                        Set<Object> members = redisTemplate.opsForSet().members(cacheKey);
                        if (members != null && !members.isEmpty()) {
                            return members.stream()
                                    .map(Object::toString)
                                    .collect(Collectors.toList());
                        }
                    }
                }
                
                // 降级处理
                log.warn("获取分布式锁失败，直接查询数据库 - key: {}", cacheKey);
                List<String> result = dataLoader.get();
                return result != null ? result : new ArrayList<>();
            }
            
        } catch (Exception e) {
            log.error("安全获取群组成员失败 - key: {}", cacheKey, e);
            try {
                List<String> result = dataLoader.get();
                return result != null ? result : new ArrayList<>();
            } catch (Exception ex) {
                log.error("数据库查询群组成员失败 - key: {}", cacheKey, ex);
                return new ArrayList<>();
            }
        }
    }
    
    @Override
    public boolean isGroupMember(String groupId, String userId) {
        try {
            // 优化：直接获取群组成员列表（带缓存安全保护），然后检查用户是否在其中
            List<String> memberIds = getGroupMemberIds(groupId);
            boolean isMember = memberIds.contains(userId);
            
            log.debug("检查群组成员身份 - 群组ID: {}, 用户ID: {}, 是否成员: {}", groupId, userId, isMember);
            return isMember;
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
            String key = RedisKeyConstants.getGroupMembersKey(groupId);
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
            String key = RedisKeyConstants.getGroupMembersKey(groupId);
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
    @Override
    public int getGroupMemberCount(String groupId) {
        try {
            // 优化：直接获取群组成员列表（带缓存安全保护），然后返回数量
            List<String> memberIds = getGroupMemberIds(groupId);
            return memberIds.size();
        } catch (Exception e) {
            log.error("获取群组成员数量失败 - 群组ID: {}", groupId, e);
            return 0;
        }
    }

    /**
     * 从数据库查询群组成员列表
     * @param groupId 群组ID
     * @return 成员ID列表，如果群组不存在返回null（由缓存管理器处理空值缓存）
     */
    private List<String> getGroupMemberIdsFromDb(String groupId) {
        try {
            // 将群组ID转换为会话ID格式
            String conversationId = convertGroupIdToConversationId(groupId);

            // 查询user_conversation_list表，获取该会话的所有用户
            LambdaQueryWrapper<UserConversationList> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UserConversationList::getConversationId, conversationId)
                   .select(UserConversationList::getUserId);

            List<UserConversationList> userConversations = userConversationListMapper.selectList(wrapper);

            if (userConversations == null || userConversations.isEmpty()) {
                log.debug("群组不存在或无成员 - 群组ID: {}, 会话ID: {}", groupId, conversationId);
                return null; // 返回null让缓存管理器缓存空值
            }

            List<String> memberIds = userConversations.stream()
                    .map(uc -> String.valueOf(uc.getUserId()))
                    .collect(Collectors.toList());

            log.debug("从数据库查询群组成员 - 群组ID: {}, 会话ID: {}, 成员数: {}",
                    groupId, conversationId, memberIds.size());

            return memberIds;
        } catch (Exception e) {
            log.error("从数据库查询群组成员失败 - 群组ID: {}", groupId, e);
            return null; // 异常时返回null，让缓存管理器使用默认值
        }
    }

    // isGroupMemberFromDb方法已移除，统一使用getGroupMemberIdsFromDb然后检查包含关系

    @Override
    public boolean isSmallGroup(String groupId) {
        try {
            int memberCount = getGroupMemberCount(groupId);
            boolean isSmall = memberCount < RedisKeyConstants.SMALL_GROUP_THRESHOLD;

            log.debug("判断群组规模 - 群组ID: {}, 成员数: {}, 是否小群: {}", groupId, memberCount, isSmall);
            return isSmall;

        } catch (Exception e) {
            log.error("判断群组规模失败 - 群组ID: {}", groupId, e);
            // 出错时默认认为是大群，避免性能问题
            return false;
        }
    }



    /**
     * 将群组ID转换为会话ID格式
     * @param groupId 群组ID
     * @return 会话ID
     */
    private String convertGroupIdToConversationId(String groupId) {
        // 如果已经是group_开头的格式，直接返回
        if (groupId.startsWith("group_")) {
            return groupId;
        }
        // 否则添加group_前缀
        return "group_" + groupId;
    }
}