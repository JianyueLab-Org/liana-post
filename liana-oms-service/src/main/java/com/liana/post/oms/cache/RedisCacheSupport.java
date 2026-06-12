package com.liana.post.oms.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.liana.post.common.util.JsonUtil;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.function.Supplier;

public final class RedisCacheSupport {

    private RedisCacheSupport() {
    }

    public static <T> T getOrLoad(StringRedisTemplate redisTemplate, String key, Class<T> type, Duration ttl, Supplier<T> loader) {
        if (redisTemplate != null) {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null && !cached.isBlank()) {
                T value = JsonUtil.fromJson(cached, type);
                if (value != null) {
                    return value;
                }
            }
        }
        T loaded = loader.get();
        if (redisTemplate != null && loaded != null) {
            redisTemplate.opsForValue().set(key, JsonUtil.toJson(loaded), ttl);
        }
        return loaded;
    }

    public static <T> T getOrLoad(StringRedisTemplate redisTemplate, String key, TypeReference<T> typeReference, Duration ttl, Supplier<T> loader) {
        if (redisTemplate != null) {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null && !cached.isBlank()) {
                T value = JsonUtil.fromJson(cached, typeReference);
                if (value != null) {
                    return value;
                }
            }
        }
        T loaded = loader.get();
        if (redisTemplate != null && loaded != null) {
            redisTemplate.opsForValue().set(key, JsonUtil.toJson(loaded), ttl);
        }
        return loaded;
    }

    public static <T> void put(StringRedisTemplate redisTemplate, String key, T value, Duration ttl) {
        if (redisTemplate == null || value == null) {
            return;
        }
        redisTemplate.opsForValue().set(key, JsonUtil.toJson(value), ttl);
    }

    public static void evict(StringRedisTemplate redisTemplate, String key) {
        if (redisTemplate != null) {
            redisTemplate.delete(key);
        }
    }

    public static void evictLike(StringRedisTemplate redisTemplate, String prefix) {
        if (redisTemplate == null) {
            return;
        }
        var keys = redisTemplate.keys(prefix + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
