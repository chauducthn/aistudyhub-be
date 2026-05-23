package com.studyhub.aistudyhubbe.config;

import com.studyhub.aistudyhubbe.entity.Role;
import com.studyhub.aistudyhubbe.entity.User;
import com.studyhub.aistudyhubbe.entity.UserStatus;
import com.studyhub.aistudyhubbe.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminAccountSeeder implements CommandLineRunner {

    private final AdminSeedProperties adminSeedProperties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminAccountSeeder(
            AdminSeedProperties adminSeedProperties,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.adminSeedProperties = adminSeedProperties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (!adminSeedProperties.isEnabled()) {
            return;
        }

        String email = adminSeedProperties.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            return;
        }

        User admin = new User();
        admin.setEmail(email);
        admin.setFullName(adminSeedProperties.getFullName().trim());
        admin.setPasswordHash(passwordEncoder.encode(adminSeedProperties.getPassword()));
        admin.setRole(Role.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);

        userRepository.save(admin);
    }
}
