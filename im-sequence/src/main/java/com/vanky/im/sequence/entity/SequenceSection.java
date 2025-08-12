package com.vanky.im.sequence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 序列号分段实体类
 * 对应数据库表：sequence_section
 *
 * @author vanky
 * @since 2025-08-11
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("sequence_section")
public class SequenceSection {

    /**
     * 自增主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 分段业务唯一键, 例如 u_{section_id} 或 c_{section_id}
     */
    @TableField("section_key")
    private String sectionKey;

    /**
     * 该分段已分配的序列号上限
     */
    @TableField("max_seq")
    private Long maxSeq;

    /**
     * 每次持久化的步长
     */
    @TableField("step")
    private Integer step;

    /**
     * 最后更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 乐观锁版本号
     */
    @TableField("version")
    @Version
    private Long version;
}
