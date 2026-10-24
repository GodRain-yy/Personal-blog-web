package com.example.blog.controller;

import com.example.blog.common.BusinessException;
import com.example.blog.common.ErrorCode;
import com.example.blog.security.LoginUser;
import com.example.blog.service.ArticleService;
import com.example.blog.service.CommentService;
import com.example.blog.service.InteractionService;
import com.example.blog.vo.InteractionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 前台文章相关页面：详情页、归档页。
 */
@Controller
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    private final CommentService commentService;

    private final InteractionService interactionService;

    @GetMapping("/articles/{id}")
    public String detail(@PathVariable Long id,
                         @AuthenticationPrincipal LoginUser loginUser,
                         Model model) {
        var article = articleService.getDetail(id);
        if (article == null) {
            // 未发布、回收站或不存在，一律按 404 处理，避免泄露草稿内容
            throw new BusinessException(ErrorCode.NOT_FOUND, "文章不存在或已下线");
        }
        Long userId = loginUser == null ? null : loginUser.getId();
        InteractionVO interaction = interactionService.getStatus(userId, id);
        model.addAttribute("article", article);
        model.addAttribute("comments", commentService.listByArticle(id));
        model.addAttribute("currentUserId", userId);
        model.addAttribute("isAdmin", loginUser != null && loginUser.isAdmin());
        model.addAttribute("liked", interaction.isLiked());
        model.addAttribute("favorited", interaction.isFavorited());
        return "article/detail";
    }

    @GetMapping("/archives")
    public String archive(Model model) {
        model.addAttribute("groups", articleService.getArchive());
        return "archive";
    }
}
