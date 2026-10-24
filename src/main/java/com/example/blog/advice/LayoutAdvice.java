package com.example.blog.advice;

import com.example.blog.entity.Category;
import com.example.blog.entity.Tag;
import com.example.blog.mapper.CategoryMapper;
import com.example.blog.mapper.TagMapper;
import com.example.blog.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

/**
 * 前台公共数据（侧边栏分类与标签）。
 * 抽到这里，避免每个前台 Controller 重复注入。
 */
@ControllerAdvice
@RequiredArgsConstructor
public class LayoutAdvice {

    private final CategoryMapper categoryMapper;

    private final TagMapper tagMapper;

    @ModelAttribute("categories")
    public List<Category> categories() {
        List<Category> list = categoryMapper.selectList(null);
        return list == null ? List.of() : list;
    }

    @ModelAttribute("tags")
    public List<Tag> tags() {
        List<Tag> list = tagMapper.selectList(null);
        return list == null ? List.of() : list;
    }

    /** 是否已登录（供导航栏切换"登录/注册"与"用户名/登出"） */
    @ModelAttribute("isLoggedIn")
    public boolean isLoggedIn() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);
    }

    /** 当前登录显示名；未登录返回空串 */
    @ModelAttribute("currentUsername")
    public String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken) {
            return "";
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof LoginUser loginUser) {
            return loginUser.getDisplayName();
        }
        return auth.getName();
    }

    /** 当前登录用户是否为管理员；未登录返回 false */
    @ModelAttribute("isAdmin")
    public boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
