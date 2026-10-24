package com.example.blog.controller;

import com.example.blog.common.BusinessException;
import com.example.blog.security.LoginUser;
import com.example.blog.service.ArticleService;
import com.example.blog.service.CategoryService;
import com.example.blog.service.CommentService;
import com.example.blog.service.TagService;
import com.example.blog.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 后台入口：概览、分类管理、标签管理。
 */
@Slf4j
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;

    private final CategoryService categoryService;

    private final TagService tagService;

    private final ArticleService articleService;

    private final CommentService commentService;

    @GetMapping
    public String index(@AuthenticationPrincipal LoginUser loginUser, Model model) {
        model.addAttribute("loginUser", loginUser);
        model.addAttribute("userCount", userService.countUsers());
        model.addAttribute("articleCount", articleService.count());
        model.addAttribute("categoryCount", categoryService.count());
        model.addAttribute("tagCount", tagService.count());
        model.addAttribute("commentCount", commentService.count());
        return "admin/index";
    }

    @GetMapping("/category")
    public String categoryList(Model model) {
        model.addAttribute("categories", categoryService.listAll());
        return "admin/category";
    }

    @PostMapping("/category/save")
    public String saveCategory(@RequestParam(required = false) Long id,
                               @RequestParam String name,
                               @RequestParam(defaultValue = "0") Integer sort,
                               RedirectAttributes redirectAttributes) {
        try {
            categoryService.saveCategory(id, name, sort);
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/category";
    }

    @PostMapping("/category/{id}/delete")
    public String deleteCategory(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            categoryService.deleteCategory(id);
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/category";
    }

    @GetMapping("/user")
    public String userList(Model model) {
        model.addAttribute("users", userService.listUsers());
        return "admin/user";
    }

    @GetMapping("/tag")
    public String tagList(Model model) {
        model.addAttribute("tags", tagService.listAll());
        return "admin/tag";
    }

    @PostMapping("/tag/save")
    public String saveTag(@RequestParam(required = false) Long id,
                          @RequestParam String name,
                          RedirectAttributes redirectAttributes) {
        try {
            tagService.saveTag(id, name);
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/tag";
    }

    @PostMapping("/tag/{id}/delete")
    public String deleteTag(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            tagService.deleteTag(id);
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/tag";
    }
}
