package com.vanky.im.message.service;

import java.util.List;
import java.util.Set;

/**
 * 群组成员服务接口
 * 负责群组成员管理
 */
public interface GroupMemberService {

    /**
     * 获取群组所有成员ID
     * @param groupId 群组ID
     * @return 成员ID列表
     */
    List<String> getGroupMemberIds(String groupId);
    
    /**
     * 判断用户是否为群组成员
     * @param groupId 群组ID
     * @param userId 用户ID
     * @return 是否为成员
     */
    boolean isGroupMember(String groupId, String userId);
    
    /**
     * 获取群组在线成员
     * @param groupId 群组ID
     * @return 在线成员ID集合
     */
    Set<String> getOnlineGroupMembers(String groupId);
} 