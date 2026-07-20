package com.studyhub.aistudyhubbe.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableAsync
@EnableConfigurationProperties({
        JwtProperties.class,
        AuthProperties.class,
        AdminSeedProperties.class,
        AiProperties.class,
        RagProperties.class,
        CacheProperties.class
})
public class AppConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
