package com.studyhub.aistudyhubbe.config;

import com.studyhub.aistudyhubbe.service.AvatarStorageService;
import com.studyhub.aistudyhubbe.service.DocumentStorageService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    private final AvatarStorageService avatarStorageService;
    private final DocumentStorageService documentStorageService;

    public StaticResourceConfig(
            AvatarStorageService avatarStorageService,
            DocumentStorageService documentStorageService) {
        this.avatarStorageService = avatarStorageService;
        this.documentStorageService = documentStorageService;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String avatarLocation = avatarStorageService.getAvatarStoragePath().toUri().toString();
        registry.addResourceHandler("/uploads/avatars/**")
                .addResourceLocations(avatarLocation);

        String documentLocation = documentStorageService.getDocumentStoragePath().toUri().toString();
        registry.addResourceHandler("/uploads/documents/**")
                .addResourceLocations(documentLocation);
    }
}
