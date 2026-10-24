package com.example.blog.controller;

import com.example.blog.common.BusinessException;
import com.example.blog.common.ErrorCode;
import com.example.blog.common.Result;
import com.example.blog.security.LoginUser;
import com.example.blog.service.InteractionService;
import com.example.blog.vo.InteractionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 点赞 / 收藏交互接口（返回 JSON，符合「Ajax 不返回 HTML 片段」的架构纪律）。
 * 未登录访问会被 SecurityConfig 的 /api/** 规则拦截返回 401。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class InteractionController {

    private final InteractionService interactionService;

    @PostMapping("/like/toggle")
    public Result<InteractionVO> toggleLike(@AuthenticationPrincipal LoginUser loginUser,
                                           Long articleId) {
        assertLogin(loginUser);
        return Result.ok(interactionService.toggleLike(loginUser.getId(), articleId));
    }

    @PostMapping("/favorite/toggle")
    public Result<InteractionVO> toggleFavorite(@AuthenticationPrincipal LoginUser loginUser,
                                                Long articleId) {
        assertLogin(loginUser);
        return Result.ok(interactionService.toggleFavorite(loginUser.getId(), articleId));
    }

    @GetMapping("/interaction/{articleId}")
    public Result<InteractionVO> status(@AuthenticationPrincipal LoginUser loginUser,
                                       @PathVariable Long articleId) {
        Long userId = loginUser == null ? null : loginUser.getId();
        return Result.ok(interactionService.getStatus(userId, articleId));
    }

    private void assertLogin(LoginUser loginUser) {
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
    }
}
