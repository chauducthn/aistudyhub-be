package com.studyhub.aistudyhubbe.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class PerformanceWebConfig implements WebMvcConfigurer {

    private final ApiPerformanceInterceptor apiPerformanceInterceptor;
    private final Path uploadRoot;

    public PerformanceWebConfig(
            ApiPerformanceInterceptor apiPerformanceInterceptor,
            @Value("${app.storage.upload-root}") String uploadRoot) {
        this.apiPerformanceInterceptor = apiPerformanceInterceptor;
        this.uploadRoot = Paths.get(uploadRoot).toAbsolutePath().normalize();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiPerformanceInterceptor).addPathPatterns("/api/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadRoot.toUri().toString());
    }
}
