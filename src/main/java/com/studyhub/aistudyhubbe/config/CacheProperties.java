package com.studyhub.aistudyhubbe.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cache")
public class CacheProperties {

    private int ttlSeconds = 60;
    private int maximumSize = 500;
    private long slowApiThresholdMs = 1500;

    public int getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(int ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public int getMaximumSize() {
        return maximumSize;
    }

    public void setMaximumSize(int maximumSize) {
        this.maximumSize = maximumSize;
    }

    public long getSlowApiThresholdMs() {
        return slowApiThresholdMs;
    }

    public void setSlowApiThresholdMs(long slowApiThresholdMs) {
        this.slowApiThresholdMs = slowApiThresholdMs;
    }
}
