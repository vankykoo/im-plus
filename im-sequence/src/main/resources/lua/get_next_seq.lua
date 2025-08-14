-- 获取下一个序列号的Lua脚本
-- 实现基于Redis Hash的原子操作，避免竞态条件
--
-- KEYS[1]: Redis Hash key, 格式: "seq:section:user_123" 或 "seq:section:conversation_456"
-- ARGV[1]: step size, 步长，如 10000
-- ARGV[2]: initial value, 初始值（可选，默认为0）
--
-- 返回值:
-- 成功: {序列号, "PERSIST", 新的最大序列号} 或 {序列号, "NOP"}
-- 失败: {"-1", "ERROR", 错误信息}

local section_key = KEYS[1]
local step = tonumber(ARGV[1])
local initial_value = tonumber(ARGV[2]) or 0

-- 参数验证
if not section_key or section_key == "" then
    return {"-1", "ERROR", "section_key is empty"}
end

if not step or step <= 0 then
    return {"-1", "ERROR", "invalid step size"}
end

if initial_value < 0 then
    return {"-1", "ERROR", "invalid initial value"}
end

-- 获取当前值和最大值
local current_values = redis.call('HMGET', section_key, 'cur_seq', 'max_seq')
local cur_seq = tonumber(current_values[1])
local max_seq = tonumber(current_values[2])

-- 初始化逻辑：如果Hash或字段不存在
if not cur_seq then
    cur_seq = initial_value
end
if not max_seq then
    max_seq = initial_value
end

-- 递增当前seq
cur_seq = cur_seq + 1

-- 检查是否超过上限
if cur_seq > max_seq then
    -- 超过上限，将上限增加一个步长
    max_seq = max_seq + step
    
    -- 更新当前值和新上限到Redis
    redis.call('HMSET', section_key, 'cur_seq', cur_seq, 'max_seq', max_seq)
    
    -- 设置过期时间（7天 = 604800秒）
    redis.call('EXPIRE', section_key, 604800)
    
    -- 返回新生成的seq和"需要持久化"的标志
    return {tostring(cur_seq), "PERSIST", tostring(max_seq)}
else
    -- 未超过上限，只更新当前值
    redis.call('HSET', section_key, 'cur_seq', cur_seq)
    
    -- 返回新生成的seq和"无需持久化"的标志
    return {tostring(cur_seq), "NOP"}
end
