package com.vanky.im.user.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户状态数据传输对象
 * 遵循单一职责原则，专门负责用户状态信息的传输
 * 
 * @author vanky
 * @since 2025-08-14
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserStatusDTO {
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 用户状态码：1-正常，0-禁用，-1-删除，2-冻结
     */
    private Integer status;
    
    /**
     * 状态描述
     */
    private String statusDesc;
    
    /**
     * 状态更新时间戳
     */
    private Long updateTime;
    
    /**
     * 构造方法 - 基本状态信息
     */
    public UserStatusDTO(String userId, Integer status, String statusDesc) {
        this.userId = userId;
        this.status = status;
        this.statusDesc = statusDesc;
        this.updateTime = System.currentTimeMillis();
    }
    
    /**
     * 判断用户是否为正常状态
     */
    public boolean isNormal() {
        return status != null && status == 1;
    }
    
    /**
     * 判断用户是否被禁用
     */
    public boolean isDisabled() {
        return status != null && status == 0;
    }
    
    /**
     * 判断用户是否被删除
     */
    public boolean isDeleted() {
        return status != null && status == -1;
    }
    
    /**
     * 判断用户是否被冻结
     */
    public boolean isFrozen() {
        return status != null && status == 2;
    }
}
