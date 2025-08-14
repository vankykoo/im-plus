package com.vanky.im.sequence.service;

import com.vanky.im.sequence.constant.SequenceConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * Lua脚本服务
 * 负责加载和执行Redis Lua脚本
 * 
 * @author vanky
 * @since 2025-08-11
 */
@Slf4j
@Service
public class LuaScriptService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 获取下一个序列号的Lua脚本
     */
    private DefaultRedisScript<List> getNextSeqScript;

    /**
     * 初始化Lua脚本
     */
    @PostConstruct
    public void initScripts() {
        try {
            // 加载获取下一个序列号的脚本
            getNextSeqScript = new DefaultRedisScript<>();
            ClassPathResource resource = new ClassPathResource("lua/get_next_seq.lua");
            String scriptContent = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            getNextSeqScript.setScriptText(scriptContent);
            getNextSeqScript.setResultType(List.class);
            
            log.info("Lua scripts initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize Lua scripts", e);
            throw new RuntimeException("Failed to initialize Lua scripts", e);
        }
    }

    /**
     * 执行获取下一个序列号的脚本
     *
     * @param sectionKey Redis Hash key
     * @param stepSize 步长
     * @return 脚本执行结果 [序列号, 操作类型, 最大序列号(可选)]
     */
    public List<String> executeGetNextSeq(String sectionKey, int stepSize) {
        return executeGetNextSeq(sectionKey, stepSize, 0L);
    }

    /**
     * 执行获取下一个序列号的脚本（支持初始值）
     *
     * @param sectionKey Redis Hash key
     * @param stepSize 步长
     * @param initialValue 初始值
     * @return 脚本执行结果 [序列号, 操作类型, 最大序列号(可选)]
     */
    public List<String> executeGetNextSeq(String sectionKey, int stepSize, long initialValue) {
        try {
            List<String> keys = Collections.singletonList(sectionKey);
            Object[] args = {String.valueOf(stepSize), String.valueOf(initialValue)};

            @SuppressWarnings("unchecked")
            List<String> result = (List<String>) stringRedisTemplate.execute(getNextSeqScript, keys, args);

            if (result == null || result.isEmpty()) {
                log.error("Lua script returned null or empty result for key: {}", sectionKey);
                return Collections.singletonList("-1");
            }

            // 验证返回结果
            String seqStr = result.get(0);
            if ("-1".equals(seqStr)) {
                log.error("Lua script execution failed for key: {}, result: {}", sectionKey, result);
            } else {
                log.debug("Lua script executed successfully for key: {}, seq: {}, action: {}, initialValue: {}",
                         sectionKey, seqStr, result.size() > 1 ? result.get(1) : "NOP", initialValue);
            }

            return result;
        } catch (Exception e) {
            log.error("Failed to execute Lua script for key: {}, initialValue: {}", sectionKey, initialValue, e);
            return Collections.singletonList("-1");
        }
    }

    /**
     * 检查脚本是否已初始化
     * 
     * @return 是否已初始化
     */
    public boolean isScriptsInitialized() {
        return getNextSeqScript != null;
    }

    /**
     * 获取脚本SHA1值（用于调试）
     * 
     * @return SHA1值
     */
    public String getScriptSha1() {
        if (getNextSeqScript != null) {
            return getNextSeqScript.getSha1();
        }
        return null;
    }
}
