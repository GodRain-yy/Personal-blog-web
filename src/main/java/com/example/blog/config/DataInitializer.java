package com.example.blog.config;

import com.example.blog.entity.User;
import com.example.blog.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 首次启动自动创建管理员账号，账号密码走配置项，不硬编码。
 * 已存在同名用户则跳过。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    @Value("${blog.init-admin.username:admin}")
    private String adminUsername;

    @Value("${blog.init-admin.password:admin123}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (userService.getByUsername(adminUsername) != null) {
            return;
        }
        User admin = new User();
        admin.setUsername(adminUsername);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setNickname("博主");
        admin.setEmail("admin@example.com");
        admin.setRole(1);
        admin.setStatus(1);
        userService.save(admin);
        log.info("已初始化管理员账号: {}", adminUsername);
    }
}
