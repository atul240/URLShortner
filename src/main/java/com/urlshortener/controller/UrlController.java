package com.urlshortener.controller;

import com.urlshortener.dto.ShortenRequest;
import com.urlshortener.service.RateLimiterService;
import com.urlshortener.service.UrlService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
public class UrlController {
    private static final Logger log = LoggerFactory.getLogger(UrlController.class);

    @Autowired
    private UrlService urlService;

    @Autowired
    private RateLimiterService rateLimiterService;

    @Value("${app.base-url}")
    private String baseUrl;

    @PostMapping("/api/shorten")
    public ResponseEntity<String> sortUrk(@RequestBody ShortenRequest request, HttpServletRequest httpServletRequest){
        log.info("POST /api/shorten received: longUrl={}, customCode={}", request.getLongUrl(), request.getCustomCode());
        String ip = httpServletRequest.getRemoteAddr();  // visitor IP (from X-Forwarded-For behind a proxy)

        // 10 req / 60 sec per visitor
        boolean allowed = rateLimiterService.allow("rate:shorten:"+ip, 10, 60);

        if(!allowed){
            return ResponseEntity.status(429).body("Too many request. Try after 1 minute.");
        }

        String shortCode = urlService.createShortUrl(request.getLongUrl(), request.getCustomCode());
        String shortUrl = baseUrl.replaceAll("/+$", "") + "/" + shortCode;
        log.info("POST /api/shorten responding 201 with shortUrl={}", shortUrl);
        return ResponseEntity.status(HttpStatus.CREATED).body(shortUrl);
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable("shortCode") String shortCode, HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();

        // 100 req / 60 sec for redirect
        boolean allowed = rateLimiterService.allow("rate:redirect:" + ip, 100, 60);
        if (!allowed) {
            return ResponseEntity.status(429).build();
        }

        String longUrl = urlService.getLongUrl(shortCode);
        if (longUrl == null) {
            return ResponseEntity.notFound().build();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(longUrl));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    @GetMapping("/api/stats/{shortCode}")
    public ResponseEntity<String> getStats(@PathVariable("shortCode") String shortCode) {
        String clicks = urlService.getClickCount(shortCode);
        String url = urlService.getLongUrlWithoutCount(shortCode);
        if (url == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok("ShortCode: " + shortCode + ", LongUrl: " + url + ", Clicks: " + clicks);
    }

}
