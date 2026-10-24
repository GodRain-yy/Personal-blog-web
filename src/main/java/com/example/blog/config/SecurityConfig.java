package com.example.blog.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 安全过滤链。
     * 注意：CSRF 保持开启（Thymeleaf 表单用 th:action 会自动注入 token，
     * Ajax 需从 meta 标签取 token 放进请求头，见 templates 中的用法）。
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // 后台必须管理员
                .requestMatchers("/admin/**").hasRole("ADMIN")
                // Ajax 接口与评论提交需登录（未登录时 /api/** 返回 401，见下方 entryPoint）
                .requestMatchers("/api/**", "/comments", "/comments/**").authenticated()
                // 前台公开页面
                .requestMatchers(
                    "/", "/index", "/hot", "/articles/**", "/archives", "/categories",
                    "/tags/**", "/search", "/login", "/register",
                    "/static/**", "/uploads/**", "/error").permitAll()
                // 其余（含未知路径）放行，交由错误控制器返回 404，而不是 401，避免泄露路径存在性
                .anyRequest().permitAll())
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/")
                .permitAll())
            .rememberMe(remember -> remember
                .key("blog-remember-me-key")
                .tokenValiditySeconds(7 * 24 * 3600))
            // 未登录处理：API 请求返回 401（前端拿到 JSON 而非 HTML），页面请求跳登录页
            // 已登录但权限不足：API 返回 JSON 403，页面返回 403（而非默认 500）
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    if (request.getRequestURI().startsWith("/api/")) {
                        response.sendError(HttpStatus.UNAUTHORIZED.value(), "请先登录");
                    } else {
                        response.sendRedirect("/login");
                    }
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    if (request.getRequestURI().startsWith("/api/")) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"code\":403,\"message\":\"没有权限\"}");
                    } else {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN, "没有权限");
                    }
                }))
            .csrf(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
