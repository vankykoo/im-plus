package com.vanky.im.common.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author vanky
 * @create 2025/6/5
 * @description Redis工具类，封装常用的Redis操作
 */
@Component
public class RedisUtils {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 获取RedisTemplate对象，用于执行未封装的操作
     * @return RedisTemplate对象
     */
    public RedisTemplate<String, Object> getRedisTemplate() {
        return redisTemplate;
    }
    
    /**
     * 获取StringRedisTemplate对象，用于执行未封装的操作
     * @return StringRedisTemplate对象
     */
    public StringRedisTemplate getStringRedisTemplate() {
        return stringRedisTemplate;
    }

    // ============================== 通用操作 ==============================

    /**
     * 设置过期时间
     *
     * @param key     键
     * @param timeout 时间
     * @param unit    时间单位
     * @return 是否成功
     */
    public Boolean expire(String key, long timeout, TimeUnit unit) {
        return redisTemplate.expire(key, timeout, unit);
    }

    /**
     * 获取过期时间
     *
     * @param key 键
     * @return 过期时间（秒）
     */
    public Long getExpire(String key) {
        return redisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    /**
     * 判断key是否存在
     *
     * @param key 键
     * @return 是否存在
     */
    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    /**
     * 删除缓存
     *
     * @param keys 可以传一个或多个值
     */
    public void delete(String... keys) {
        if (keys != null && keys.length > 0) {
            if (keys.length == 1) {
                redisTemplate.delete(keys[0]);
            } else {
                redisTemplate.delete((Collection<String>) List.of(keys));
            }
        }
    }

    // ============================== String类型操作 ==============================

    /**
     * 获取字符串值
     *
     * @param key 键
     * @return 值
     */
    public Object get(String key) {
        return key == null ? null : redisTemplate.opsForValue().get(key);
    }

    /**
     * 获取字符串值（String类型）
     *
     * @param key 键
     * @return 值
     */
    public String getString(String key) {
        return key == null ? null : stringRedisTemplate.opsForValue().get(key);
    }

    /**
     * 设置字符串值
     *
     * @param key   键
     * @param value 值
     */
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * 设置字符串值（String类型）
     *
     * @param key   键
     * @param value 值
     */
    public void setString(String key, String value) {
        stringRedisTemplate.opsForValue().set(key, value);
    }

    /**
     * 设置字符串值并设置过期时间
     *
     * @param key     键
     * @param value   值
     * @param timeout 时间
     * @param unit    时间单位
     */
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    /**
     * 设置字符串值并设置过期时间（String类型）
     *
     * @param key     键
     * @param value   值
     * @param timeout 时间
     * @param unit    时间单位
     */
    public void setString(String key, String value, long timeout, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    /**
     * 递增
     *
     * @param key   键
     * @param delta 递增因子
     * @return 递增后的值
     */
    public Long increment(String key, long delta) {
        return redisTemplate.opsForValue().increment(key, delta);
    }

    /**
     * 递减
     *
     * @param key   键
     * @param delta 递减因子
     * @return 递减后的值
     */
    public Long decrement(String key, long delta) {
        return redisTemplate.opsForValue().decrement(key, delta);
    }

    // ============================== Hash类型操作 ==============================

    /**
     * 获取Hash中的字段值
     *
     * @param key     键
     * @param hashKey 字段
     * @return 值
     */
    public Object hashGet(String key, String hashKey) {
        return redisTemplate.opsForHash().get(key, hashKey);
    }

    /**
     * 获取Hash中的所有字段和值
     *
     * @param key 键
     * @return 对应的多个键值
     */
    public Map<Object, Object> hashGetAll(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    /**
     * 设置Hash中的字段值
     *
     * @param key     键
     * @param hashKey 字段
     * @param value   值
     */
    public void hashSet(String key, String hashKey, Object value) {
        redisTemplate.opsForHash().put(key, hashKey, value);
    }

    /**
     * 设置Hash中的多个字段值
     *
     * @param key 键
     * @param map 对应多个键值
     */
    public void hashSetAll(String key, Map<String, Object> map) {
        redisTemplate.opsForHash().putAll(key, map);
    }

    /**
     * 删除Hash中的字段
     *
     * @param key      键
     * @param hashKeys 字段
     * @return 删除的数量
     */
    public Long hashDelete(String key, Object... hashKeys) {
        return redisTemplate.opsForHash().delete(key, hashKeys);
    }

    /**
     * 判断Hash中是否存在字段
     *
     * @param key     键
     * @param hashKey 字段
     * @return 是否存在
     */
    public Boolean hashHasKey(String key, String hashKey) {
        return redisTemplate.opsForHash().hasKey(key, hashKey);
    }

    /**
     * Hash递增
     *
     * @param key     键
     * @param hashKey 字段
     * @param delta   递增因子
     * @return 递增后的值
     */
    public Long hashIncrement(String key, String hashKey, long delta) {
        return redisTemplate.opsForHash().increment(key, hashKey, delta);
    }

    // ============================== List类型操作 ==============================

    /**
     * 获取List中的元素
     *
     * @param key   键
     * @param start 开始索引
     * @param end   结束索引
     * @return 元素列表
     */
    public List<Object> listRange(String key, long start, long end) {
        return redisTemplate.opsForList().range(key, start, end);
    }

    /**
     * 获取List的长度
     *
     * @param key 键
     * @return 长度
     */
    public Long listSize(String key) {
        return redisTemplate.opsForList().size(key);
    }

    /**
     * 通过索引获取List中的元素
     *
     * @param key   键
     * @param index 索引
     * @return 元素
     */
    public Object listIndex(String key, long index) {
        return redisTemplate.opsForList().index(key, index);
    }

    /**
     * 将元素添加到List右端
     *
     * @param key   键
     * @param value 值
     * @return 添加后的长度
     */
    public Long listRightPush(String key, Object value) {
        return redisTemplate.opsForList().rightPush(key, value);
    }

    /**
     * 将多个元素添加到List右端
     *
     * @param key    键
     * @param values 值
     * @return 添加后的长度
     */
    public Long listRightPushAll(String key, Object... values) {
        return redisTemplate.opsForList().rightPushAll(key, values);
    }

    /**
     * 将元素添加到List左端
     *
     * @param key   键
     * @param value 值
     * @return 添加后的长度
     */
    public Long listLeftPush(String key, Object value) {
        return redisTemplate.opsForList().leftPush(key, value);
    }

    /**
     * 将多个元素添加到List左端
     *
     * @param key    键
     * @param values 值
     * @return 添加后的长度
     */
    public Long listLeftPushAll(String key, Object... values) {
        return redisTemplate.opsForList().leftPushAll(key, values);
    }

    /**
     * 从List右端弹出元素
     *
     * @param key 键
     * @return 弹出的元素
     */
    public Object listRightPop(String key) {
        return redisTemplate.opsForList().rightPop(key);
    }

    /**
     * 从List左端弹出元素
     *
     * @param key 键
     * @return 弹出的元素
     */
    public Object listLeftPop(String key) {
        return redisTemplate.opsForList().leftPop(key);
    }

    // ============================== Set类型操作 ==============================

    /**
     * 获取Set中的所有元素
     *
     * @param key 键
     * @return 元素集合
     */
    public Set<Object> setMembers(String key) {
        return redisTemplate.opsForSet().members(key);
    }

    /**
     * 判断Set中是否存在元素
     *
     * @param key   键
     * @param value 值
     * @return 是否存在
     */
    public Boolean setIsMember(String key, Object value) {
        return redisTemplate.opsForSet().isMember(key, value);
    }

    /**
     * 向Set中添加元素
     *
     * @param key    键
     * @param values 值
     * @return 添加的数量
     */
    public Long setAdd(String key, Object... values) {
        return redisTemplate.opsForSet().add(key, values);
    }

    /**
     * 从Set中删除元素
     *
     * @param key    键
     * @param values 值
     * @return 删除的数量
     */
    public Long setRemove(String key, Object... values) {
        return redisTemplate.opsForSet().remove(key, values);
    }

    /**
     * 获取Set的大小
     *
     * @param key 键
     * @return 大小
     */
    public Long setSize(String key) {
        return redisTemplate.opsForSet().size(key);
    }

    // ============================== ZSet类型操作 ==============================

    /**
     * 向ZSet中添加元素
     *
     * @param key   键
     * @param value 值
     * @param score 分数
     * @return 是否成功
     */
    public Boolean zSetAdd(String key, Object value, double score) {
        return redisTemplate.opsForZSet().add(key, value, score);
    }

    /**
     * 获取ZSet中指定范围的元素
     *
     * @param key   键
     * @param start 开始索引
     * @param end   结束索引
     * @return 元素集合
     */
    public Set<Object> zSetRange(String key, long start, long end) {
        return redisTemplate.opsForZSet().range(key, start, end);
    }

    /**
     * 获取ZSet中指定分数范围的元素
     *
     * @param key 键
     * @param min 最小分数
     * @param max 最大分数
     * @return 元素集合
     */
    public Set<Object> zSetRangeByScore(String key, double min, double max) {
        return redisTemplate.opsForZSet().rangeByScore(key, min, max);
    }

    /**
     * 获取ZSet中元素的分数
     *
     * @param key   键
     * @param value 值
     * @return 分数
     */
    public Double zSetScore(String key, Object value) {
        return redisTemplate.opsForZSet().score(key, value);
    }

    /**
     * 从ZSet中删除元素
     *
     * @param key    键
     * @param values 值
     * @return 删除的数量
     */
    public Long zSetRemove(String key, Object... values) {
        return redisTemplate.opsForZSet().remove(key, values);
    }

    /**
     * 获取ZSet的大小
     *
     * @param key 键
     * @return 大小
     */
    public Long zSetSize(String key) {
        return redisTemplate.opsForZSet().size(key);
    }

    /**
     * 增加ZSet中元素的分数
     *
     * @param key   键
     * @param value 值
     * @param delta 增加的分数
     * @return 新的分数
     */
    public Double zSetIncrementScore(String key, Object value, double delta) {
        return redisTemplate.opsForZSet().incrementScore(key, value, delta);
    }
}