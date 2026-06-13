package com.shortener.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.shortener.service.UrlShortenerService;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    @Bean(name = "caffeineCacheManager")
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("l1-urls");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfter(new Expiry<Object, Object>() {
                    @Override
                    public long expireAfterCreate(Object key, Object value, long currentTime) {
                        if (UrlShortenerService.isRedisDown()) {
                            return TimeUnit.MINUTES.toNanos(10);
                        } else {
                            return TimeUnit.SECONDS.toNanos(15);
                        }
                    }

                    @Override
                    public long expireAfterUpdate(Object key, Object value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }

                    @Override
                    public long expireAfterRead(Object key, Object value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                })
                .maximumSize(10000)                     // Cap memory usage
                .recordStats());
        return cacheManager;
    }
}
