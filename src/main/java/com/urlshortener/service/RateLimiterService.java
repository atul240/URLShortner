package com.urlshortener.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

@Service
public class RateLimiterService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    public boolean allow(String key, int limit, int windowSeconds) {
        Long count = redisTemplate.opsForValue().increment(key);

        if (count != null && count == 1) {
            redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
        }

        System.out.println("RateLimit key: " + key + " count: " + count + " limit: " + limit);
        return count != null && count <= limit;
    }
}