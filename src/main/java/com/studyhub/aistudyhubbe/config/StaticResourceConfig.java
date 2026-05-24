package com.studyhub.aistudyhubbe.config;

import com.studyhub.aistudyhubbe.service.AvatarStorageService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    private final AvatarStorageService avatarStorageService;

    public StaticResourceConfig(AvatarStorageService avatarStorageService) {
        this.avatarStorageService = avatarStorageService;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String avatarLocation = avatarStorageService.getAvatarStoragePath().toUri().toString();
        registry.addResourceHandler("/uploads/avatars/**")
                .addResourceLocations(avatarLocation);
    }
}
