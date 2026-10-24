package com.example.blog.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * 登录成功事件：回写最后登录时间。
 * 与认证主流程解耦，写库失败不影响登录结果。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginSuccessListener {

    private final com.example.blog.service.UserService userService;

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        Authentication auth = event.getAuthentication();
        if (auth.getPrincipal() instanceof LoginUser loginUser) {
            try {
                userService.updateLastLoginTime(loginUser.getId());
            } catch (Exception e) {
                log.warn("回写最后登录时间失败, userId={}", loginUser.getId(), e);
            }
        }
    }
}
