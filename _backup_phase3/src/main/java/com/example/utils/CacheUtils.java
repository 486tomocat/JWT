package com.example.utils;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 通用缓存工具：对象/集合的读写与按前缀批量失效。
 * 用泛型 + fastjson2 完成「对象 <-> JSON 字符串」的转换，
 * 业务层不需要关心序列化细节。
 */
@Component
public class CacheUtils {

    @Resource
    StringRedisTemplate template;

    /** 读单个对象，缓存不存在返回 null */
    public <T> T takeFromCache(String key, Class<T> dataType) {
        String s = template.opsForValue().get(key);
        if (s == null) return null;
        return JSONObject.parseObject(s).to(dataType);
    }

    /** 读集合，缓存不存在返回 null */
    public <T> List<T> takeListFromCache(String key, Class<T> itemType) {
        String s = template.opsForValue().get(key);
        if (s == null) return null;
        return JSONArray.parseArray(s).toList(itemType);
    }

    public <T> void saveToCache(String key, T data, long expire) {
        template.opsForValue().set(key, JSONObject.from(data).toJSONString(), expire, TimeUnit.SECONDS);
    }

    public <T> void saveListToCache(String key, List<T> list, long expire) {
        template.opsForValue().set(key, JSONArray.from(list).toJSONString(), expire, TimeUnit.SECONDS);
    }

    /**
     * 按前缀批量删除。
     * 注意：keys 命令在 Redis 数据量大时会阻塞，生产环境建议用 SCAN 或维护 key 集合。
     */
    public void deleteCachePattern(String key) {
        Set<String> keys = Optional.ofNullable(template.keys(key)).orElse(Collections.emptySet());
        if (!keys.isEmpty()) {
            template.delete(keys);
        }
    }

    public void deleteCache(String key) {
        template.delete(key);
    }
}
