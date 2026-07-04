package com.studyhub.aistudyhubbe.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ApiPerformanceInterceptor implements HandlerInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiPerformanceInterceptor.class);
    private static final String START_TIME_ATTRIBUTE = "apiPerfStartMs";

    private final CacheProperties cacheProperties;

    public ApiPerformanceInterceptor(CacheProperties cacheProperties) {
        this.cacheProperties = cacheProperties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME_ATTRIBUTE, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {
        Object startTime = request.getAttribute(START_TIME_ATTRIBUTE);
        if (!(startTime instanceof Long startMs)) {
            return;
        }

        long durationMs = System.currentTimeMillis() - startMs;
        if (durationMs >= cacheProperties.getSlowApiThresholdMs()) {
            LOGGER.warn(
                    "Slow API {} {} completed in {}ms (threshold {}ms)",
                    request.getMethod(),
                    request.getRequestURI(),
                    durationMs,
                    cacheProperties.getSlowApiThresholdMs());
        }
    }
}
