package com.vanky.im.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vanky.im.common.constant.RedisKeyConstants;
import com.vanky.im.common.constant.SessionConstants;
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

    // 注意：Redis key前缀和缓存配置已迁移到RedisKeyConstants类
    
    @Override
    public List<String> getGroupMemberIds(String groupId) {
        try {
            // 1. 先从Redis缓存查询
            String key = RedisKeyConstants.getGroupMembersKey(groupId);
            Set<Object> members = redisTemplate.opsForSet().members(key);

            if (members != null && !members.isEmpty()) {
                log.debug("从Redis缓存获取群组成员 - 群组ID: {}, 成员数: {}", groupId, members.size());
                return members.stream()
                        .map(Object::toString)
                        .collect(Collectors.toList());
            }

            // 2. Redis缓存为空，从数据库查询
            log.debug("Redis缓存为空，从数据库查询群组成员 - 群组ID: {}", groupId);
            List<String> memberIds = getGroupMemberIdsFromDb(groupId);

            // 3. 如果数据库中有数据，回填Redis缓存
            if (!memberIds.isEmpty()) {
                String[] memberArray = memberIds.toArray(new String[0]);
                redisTemplate.opsForSet().add(key, (Object[]) memberArray);
                redisTemplate.expire(key, RedisKeyConstants.CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
                log.info("从数据库查询到群组成员并回填缓存 - 群组ID: {}, 成员数: {}", groupId, memberIds.size());
            } else {
                log.warn("群组成员列表为空 - 群组ID: {}", groupId);
            }

            return memberIds;
        } catch (Exception e) {
            log.error("获取群组成员失败 - 群组ID: {}", groupId, e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public boolean isGroupMember(String groupId, String userId) {
        try {
            // 1. 先从Redis缓存查询
            String key = RedisKeyConstants.getGroupMembersKey(groupId);
            Boolean isMemberInCache = redisTemplate.opsForSet().isMember(key, userId);

            if (Boolean.TRUE.equals(isMemberInCache)) {
                log.debug("从Redis缓存确认群组成员身份 - 群组ID: {}, 用户ID: {}", groupId, userId);
                return true;
            }

            // 2. Redis缓存中没有找到，检查缓存是否存在
            Long cacheSize = redisTemplate.opsForSet().size(key);
            if (cacheSize != null && cacheSize > 0) {
                // 缓存存在但用户不在其中，说明用户不是群组成员
                log.debug("Redis缓存存在但用户不在群组中 - 群组ID: {}, 用户ID: {}", groupId, userId);
                return false;
            }

            // 3. 缓存不存在或为空，从数据库查询
            log.debug("Redis缓存不存在，从数据库查询群组成员身份 - 群组ID: {}, 用户ID: {}", groupId, userId);
            boolean isMemberInDb = isGroupMemberFromDb(groupId, userId);

            // 4. 如果数据库查询结果为true，可以考虑预加载整个群组成员列表到缓存
            if (isMemberInDb) {
                // 预加载群组成员列表到缓存，提高后续查询性能
                List<String> allMembers = getGroupMemberIdsFromDb(groupId);
                if (!allMembers.isEmpty()) {
                    String[] memberArray = allMembers.toArray(new String[0]);
                    redisTemplate.opsForSet().add(key, (Object[]) memberArray);
                    redisTemplate.expire(key, RedisKeyConstants.CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
                    log.info("预加载群组成员列表到缓存 - 群组ID: {}, 成员数: {}", groupId, allMembers.size());
                }
            }

            return isMemberInDb;
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
            String key = RedisKeyConstants.getGroupMembersKey(groupId);
            Long count = redisTemplate.opsForSet().size(key);
            if (count != null && count > 0) {
                return count.intValue();
            }

            // Redis缓存为空，从数据库查询
            List<String> memberIds = getGroupMemberIdsFromDb(groupId);
            return memberIds.size();
        } catch (Exception e) {
            log.error("获取群组成员数量失败 - 群组ID: {}", groupId, e);
            return 0;
        }
    }

    /**
     * 从数据库查询群组成员列表
     * @param groupId 群组ID
     * @return 成员ID列表
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

            List<String> memberIds = userConversations.stream()
                    .map(uc -> String.valueOf(uc.getUserId()))
                    .collect(Collectors.toList());

            log.debug("从数据库查询群组成员 - 群组ID: {}, 会话ID: {}, 成员数: {}",
                    groupId, conversationId, memberIds.size());

            return memberIds;
        } catch (Exception e) {
            log.error("从数据库查询群组成员失败 - 群组ID: {}", groupId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 从数据库查询用户是否为群组成员
     * @param groupId 群组ID
     * @param userId 用户ID
     * @return 是否为成员
     */
    private boolean isGroupMemberFromDb(String groupId, String userId) {
        try {
            // 将群组ID转换为会话ID格式
            String conversationId = convertGroupIdToConversationId(groupId);

            // 查询user_conversation_list表，检查用户是否在该会话中
            LambdaQueryWrapper<UserConversationList> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UserConversationList::getConversationId, conversationId)
                   .eq(UserConversationList::getUserId, Long.valueOf(userId));

            Long count = userConversationListMapper.selectCount(wrapper);
            boolean isMember = count != null && count > 0;

            log.debug("从数据库查询群组成员身份 - 群组ID: {}, 会话ID: {}, 用户ID: {}, 是否成员: {}",
                    groupId, conversationId, userId, isMember);

            return isMember;
        } catch (Exception e) {
            log.error("从数据库查询群组成员身份失败 - 群组ID: {}, 用户ID: {}", groupId, userId, e);
            return false;
        }
    }

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