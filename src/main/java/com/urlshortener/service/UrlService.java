package com.urlshortener.service;

import com.urlshortener.encoder.Base62Encoder;
import com.urlshortener.exception.CustomCodeTakenException;
import com.urlshortener.exception.InvalidRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Time;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

@Service
public class UrlService {
    private static final Logger log = LoggerFactory.getLogger(UrlService.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private Base62Encoder base62Encoder;

    private AtomicLong idCounter = new AtomicLong(100000);

    // Custom codes: letters, digits, '-' and '_' only, 1-32 characters.
    private static final Pattern CUSTOM_CODE_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,32}");

    private static final Duration TTL = Duration.ofDays(30);

    public String createShortUrl(String longUrl, String customCode){
        log.debug("createShortUrl called: longUrl={}, customCode={}", longUrl, customCode);
        validateLongUrl(longUrl);

        boolean hasCustomCode = customCode != null && !customCode.isEmpty();
        if(hasCustomCode && !CUSTOM_CODE_PATTERN.matcher(customCode).matches()){
            throw new InvalidRequestException(
                    "customCode must be 1-32 characters: letters, digits, '-' or '_'");
        }

        String shortCode;
        if(hasCustomCode){
            // SETNX: check and set in one atomic step, so two requests can't both claim the same code.
            Boolean created = redisTemplate.opsForValue().setIfAbsent(customCode, longUrl, TTL);
            if(!Boolean.TRUE.equals(created)){
                log.warn("Custom code '{}' already taken, rejecting request", customCode);
                throw new CustomCodeTakenException("Custom code already taken: " + customCode);
            }
            shortCode = customCode;
            log.info("Using custom code '{}' (not taken)", shortCode);
        }else{
            long id = idCounter.incrementAndGet();
            shortCode = base62Encoder.encode(id);
            log.info("No custom code given, generated shortCode '{}' from counter id={}", shortCode, id);
            redisTemplate.opsForValue().set(shortCode, longUrl, TTL);
        }
        // Click count 0 se start kar de
        redisTemplate.opsForValue().set("click:" + shortCode, "0", TTL);
        log.info("Stored mapping in Redis: {} -> {} (TTL 30 days)", shortCode, longUrl);
        return shortCode;
    }

    // longUrl must be present and an absolute http or https URL with a host.
    private static void validateLongUrl(String longUrl){
        if(longUrl == null || longUrl.isBlank()){
            throw new InvalidRequestException("longUrl is required");
        }
        URI uri;
        try{
            uri = new URI(longUrl);
        }catch(URISyntaxException e){
            throw new InvalidRequestException("longUrl is not a valid URL");
        }
        String scheme = uri.getScheme();
        boolean httpScheme = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        if(!httpScheme || uri.getHost() == null){
            throw new InvalidRequestException("longUrl must be an absolute http or https URL");
        }
    }

    public String getLongUrl(String shortCode){
        log.debug("Looking up shortCode '{}' in Redis", shortCode);
        String longUrl = redisTemplate.opsForValue().get(shortCode);
        redisTemplate.opsForValue().increment("click:" + shortCode);
        if(longUrl == null){
            log.info("Lookup miss for shortCode '{}' (no mapping in Redis)", shortCode);
        }else{
            log.info("Lookup hit for shortCode '{}' -> {}", shortCode, longUrl);
        }
        return longUrl;
    }

    public String getClickCount(String shortCode) {
        String count = redisTemplate.opsForValue().get("click:" + shortCode);
        return count == null ? "0" : count;
    }

    // UrlService.java me add kar
    public String getLongUrlWithoutCount(String shortCode) {
        return redisTemplate.opsForValue().get(shortCode);
    }

}
