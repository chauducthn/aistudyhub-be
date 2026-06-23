package com.studyhub.aistudyhubbe.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class PerformanceWebConfig implements WebMvcConfigurer {

    private final ApiPerformanceInterceptor apiPerformanceInterceptor;

    public PerformanceWebConfig(ApiPerformanceInterceptor apiPerformanceInterceptor) {
        this.apiPerformanceInterceptor = apiPerformanceInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiPerformanceInterceptor).addPathPatterns("/api/**");
    }
}
