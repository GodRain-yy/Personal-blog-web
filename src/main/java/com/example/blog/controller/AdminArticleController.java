package com.example.blog.controller;

import com.example.blog.dto.ArticleQuery;
import com.example.blog.dto.ArticleSaveRequest;
import com.example.blog.mapper.CategoryMapper;
import com.example.blog.security.LoginUser;
import com.example.blog.service.ArticleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 后台文章管理。D3 先用纯 textarea 编辑器跑通链路，D4 替换为 Vditor + 图片上传。
 */
@Controller
@RequestMapping("/admin/article")
@RequiredArgsConstructor
public class AdminArticleController {

    private final ArticleService articleService;

    private final CategoryMapper categoryMapper;

    @GetMapping
    public String list(@ModelAttribute ArticleQuery query, Model model) {
        model.addAttribute("pageResult", articleService.listArticles(query));
        model.addAttribute("query", query);
        return "admin/article/list";
    }

    @GetMapping("/edit")
    public String edit(@RequestParam(required = false) Long id, Model model) {
        ArticleSaveRequest form = (id == null) ? new ArticleSaveRequest() : articleService.getEditForm(id);
        model.addAttribute("form", form);
        model.addAttribute("categories", categoryMapper.selectList(null));
        return "admin/article/edit";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("form") ArticleSaveRequest form,
                       BindingResult bindingResult,
                       @AuthenticationPrincipal LoginUser loginUser,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("categories", categoryMapper.selectList(null));
            model.addAttribute("error", bindingResult.getFieldErrors().get(0).getDefaultMessage());
            return "admin/article/edit";
        }
        articleService.saveArticle(form, loginUser.getId());
        redirectAttributes.addFlashAttribute("msg", "保存成功");
        return "redirect:/admin/article";
    }

    @PostMapping("/delete")
    public String delete(@RequestParam Long id, RedirectAttributes redirectAttributes) {
        articleService.deleteArticle(id);
        redirectAttributes.addFlashAttribute("msg", "已删除");
        return "redirect:/admin/article";
    }
}
