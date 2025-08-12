package com.vanky.im.sequence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vanky.im.sequence.entity.SequenceSection;
import org.apache.ibatis.annotations.*;



import java.util.List;

/**
 * 序列号分段Mapper接口
 * 
 * @author vanky
 * @since 2025-08-11
 */
@Mapper
public interface SequenceSectionMapper extends BaseMapper<SequenceSection> {

    /**
     * 根据分段业务键查询分段信息
     *
     * @param sectionKey 分段业务键，如 u_123 或 c_456
     * @return 分段信息
     */
    @Select("SELECT * FROM sequence_section WHERE section_key = #{sectionKey}")
    SequenceSection selectBySectionKey(@Param("sectionKey") String sectionKey);

    /**
     * 插入或更新分段信息（原子操作）
     * 使用 INSERT ... ON DUPLICATE KEY UPDATE 实现高效的 upsert 操作
     *
     * @param sectionKey 分段业务键
     * @param maxSeq 新的最大序列号
     * @param step 步长
     * @return 影响行数
     */
    @Insert("INSERT INTO sequence_section (section_key, max_seq, step) " +
            "VALUES (#{sectionKey}, #{maxSeq}, #{step}) " +
            "ON DUPLICATE KEY UPDATE " +
            "max_seq = VALUES(max_seq), version = version + 1")
    int insertOrUpdateMaxSeq(@Param("sectionKey") String sectionKey,
                            @Param("maxSeq") Long maxSeq,
                            @Param("step") Integer step);

    /**
     * 批量插入或更新分段信息
     *
     * @param sections 分段信息列表
     * @return 更新行数
     */
    int batchInsertOrUpdate(@Param("sections") List<SequenceSection> sections);

    /**
     * 根据前缀查询分段列表（用于监控和统计）
     *
     * @param keyPrefix 分段键前缀，如 'u_' 或 'c_'
     * @return 分段列表
     */
    @Select("SELECT * FROM sequence_section WHERE section_key LIKE CONCAT(#{keyPrefix}, '%') ORDER BY section_key")
    List<SequenceSection> selectByKeyPrefix(@Param("keyPrefix") String keyPrefix);

    /**
     * 获取指定前缀的分段统计信息
     *
     * @param keyPrefix 分段键前缀，如 'u_' 或 'c_'
     * @return 分段数量
     */
    @Select("SELECT COUNT(*) FROM sequence_section WHERE section_key LIKE CONCAT(#{keyPrefix}, '%')")
    Long countByKeyPrefix(@Param("keyPrefix") String keyPrefix);

    /**
     * 获取所有分段的统计信息
     *
     * @return 总分段数量
     */
    @Select("SELECT COUNT(*) FROM sequence_section")
    Long countAll();
}
