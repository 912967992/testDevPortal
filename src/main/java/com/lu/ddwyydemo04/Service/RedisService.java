package com.lu.ddwyydemo04.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class RedisService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 设置缓存
     * @param key 键
     * @param value 值
     */
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * 设置缓存并指定过期时间
     * @param key 键
     * @param value 值
     * @param timeout 过期时间
     * @param unit 时间单位
     */
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    /**
     * 获取缓存
     * @param key 键
     * @return 值
     */
    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 删除缓存
     * @param key 键
     * @return 是否删除成功
     */
    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    /**
     * 批量删除缓存
     * @param keys 键集合
     * @return 删除的数量
     */
    public Long delete(List<String> keys) {
        return redisTemplate.delete(keys);
    }

    /**
     * 判断key是否存在
     * @param key 键
     * @return 是否存在
     */
    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    /**
     * 设置过期时间
     * @param key 键
     * @param timeout 过期时间
     * @param unit 时间单位
     * @return 是否设置成功
     */
    public Boolean expire(String key, long timeout, TimeUnit unit) {
        return redisTemplate.expire(key, timeout, unit);
    }

    /**
     * 获取过期时间
     * @param key 键
     * @return 过期时间（秒）
     */
    public Long getExpire(String key) {
        return redisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    /**
     * Hash操作 - 设置hash字段值
     * @param key 键
     * @param field 字段
     * @param value 值
     */
    public void hSet(String key, String field, Object value) {
        redisTemplate.opsForHash().put(key, field, value);
    }

    /**
     * Hash操作 - 获取hash字段值
     * @param key 键
     * @param field 字段
     * @return 值
     */
    public Object hGet(String key, String field) {
        return redisTemplate.opsForHash().get(key, field);
    }

    /**
     * Hash操作 - 获取整个hash
     * @param key 键
     * @return hash对象
     */
    public Map<Object, Object> hGetAll(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    /**
     * Hash操作 - 删除hash字段
     * @param key 键
     * @param fields 字段数组
     * @return 删除的数量
     */
    public Long hDelete(String key, Object... fields) {
        return redisTemplate.opsForHash().delete(key, fields);
    }

    /**
     * Hash操作 - 判断hash字段是否存在
     * @param key 键
     * @param field 字段
     * @return 是否存在
     */
    public Boolean hExists(String key, String field) {
        return redisTemplate.opsForHash().hasKey(key, field);
    }

    /**
     * List操作 - 从左边添加元素
     * @param key 键
     * @param value 值
     * @return 列表长度
     */
    public Long lPush(String key, Object value) {
        return redisTemplate.opsForList().leftPush(key, value);
    }

    /**
     * List操作 - 从右边添加元素
     * @param key 键
     * @param value 值
     * @return 列表长度
     */
    public Long rPush(String key, Object value) {
        return redisTemplate.opsForList().rightPush(key, value);
    }

    /**
     * List操作 - 从左边弹出元素
     * @param key 键
     * @return 弹出的元素
     */
    public Object lPop(String key) {
        return redisTemplate.opsForList().leftPop(key);
    }

    /**
     * List操作 - 从右边弹出元素
     * @param key 键
     * @return 弹出的元素
     */
    public Object rPop(String key) {
        return redisTemplate.opsForList().rightPop(key);
    }

    /**
     * List操作 - 获取列表长度
     * @param key 键
     * @return 长度
     */
    public Long lLen(String key) {
        return redisTemplate.opsForList().size(key);
    }

    /**
     * Set操作 - 添加元素
     * @param key 键
     * @param values 值
     * @return 添加成功的数量
     */
    public Long sAdd(String key, Object... values) {
        return redisTemplate.opsForSet().add(key, values);
    }

    /**
     * Set操作 - 移除元素
     * @param key 键
     * @param values 值
     * @return 移除的数量
     */
    public Long sRemove(String key, Object... values) {
        return redisTemplate.opsForSet().remove(key, values);
    }

    /**
     * Set操作 - 判断元素是否存在
     * @param key 键
     * @param value 值
     * @return 是否存在
     */
    public Boolean sIsMember(String key, Object value) {
        return redisTemplate.opsForSet().isMember(key, value);
    }

    /**
     * Set操作 - 获取所有元素
     * @param key 键
     * @return 元素集合
     */
    public Set<Object> sMembers(String key) {
        return redisTemplate.opsForSet().members(key);
    }

    /**
     * 原子递增
     * @param key 键
     * @param delta 增量
     * @return 递增后的值
     */
    public Long increment(String key, long delta) {
        return redisTemplate.opsForValue().increment(key, delta);
    }

    /**
     * 原子递减
     * @param key 键
     * @param delta 减量
     * @return 递减后的值
     */
    public Long decrement(String key, long delta) {
        return redisTemplate.opsForValue().decrement(key, delta);
    }

    /**
     * 获取所有匹配的键
     * @param pattern 模式
     * @return 键集合
     */
    public Set<String> keys(String pattern) {
        return redisTemplate.keys(pattern);
    }

    /**
     * 清空当前数据库
     */
    public void flushDB() {
        redisTemplate.getConnectionFactory().getConnection().flushDb();
    }
}


