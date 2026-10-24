package com.example.blog.controller;

import com.example.blog.dto.CommentRequest;
import com.example.blog.security.LoginUser;
import com.example.blog.service.CommentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 评论发表与删除。页面表单提交后重定向回详情页（PRG 模式，避免刷新重复提交）。
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/comments")
    public String add(@Valid CommentRequest request,
                      BindingResult bindingResult,
                      @AuthenticationPrincipal LoginUser loginUser,
                      HttpServletRequest httpRequest,
                      RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("commentError",
                    bindingResult.getFieldError() == null ? "评论内容不合法" : bindingResult.getFieldError().getDefaultMessage());
            return redirectToArticle(request.getArticleId());
        }
        try {
            commentService.addComment(request, loginUser.getId(), resolveIp(httpRequest));
        } catch (RuntimeException e) {
            log.warn("发表评论失败: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("commentError", e.getMessage());
        }
        return redirectToArticle(request.getArticleId());
    }

    @PostMapping("/comments/{id}/delete")
    public String delete(@PathVariable Long id,
                         @AuthenticationPrincipal LoginUser loginUser,
                         RedirectAttributes redirectAttributes) {
        try {
            commentService.deleteComment(id, loginUser.getId(), loginUser.isAdmin());
        } catch (RuntimeException e) {
            log.warn("删除评论失败: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("commentError", e.getMessage());
        }
        return "redirect:/";
    }

    private String redirectToArticle(Long articleId) {
        return "redirect:/articles/" + (articleId == null ? "" : articleId) + "#comments";
    }

    private String resolveIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
