package com.guardianpay.guardianpay.config;

import com.guardianpay.guardianpay.model.Role;
import com.guardianpay.guardianpay.model.User;
import com.guardianpay.guardianpay.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${guardianpay.admin.email}") private String adminEmail;
    @Value("${guardianpay.admin.password}") private String adminPassword;

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.findByEmail(adminEmail).isPresent()) {
            return;
        }
        User admin = new User();
        admin.setName("Admin");
        admin.setEmail(adminEmail);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);
    }
}