package com.example.blog.controller;

import com.example.blog.dto.ArticleQuery;
import com.example.blog.service.ArticleService;
import com.example.blog.vo.HotItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

/**
 * 前台首页与搜索：文章列表 + 分页。
 * 侧边栏的分类/标签由 LayoutAdvice 统一注入。
 */
@Controller
@RequiredArgsConstructor
public class IndexController {

    private final ArticleService articleService;

    @GetMapping({"/", "/index"})
    public String index(@ModelAttribute ArticleQuery query, Model model) {
        query.setStatus(1);
        model.addAttribute("pageResult", articleService.listArticles(query));
        model.addAttribute("query", query);
        return "index";
    }

    /** 搜索走 ft_title_summary 全文索引（ngram 解析器） */
    @GetMapping("/search")
    public String search(@ModelAttribute ArticleQuery query, Model model) {
        query.setStatus(1);
        model.addAttribute("pageResult", articleService.listArticles(query));
        model.addAttribute("query", query);
        return "index";
    }

    /** 热门榜：按 Redis ZSet 热度倒序 */
    @GetMapping("/hot")
    public String hot(Model model) {
        List<HotItemVO> hotList = articleService.listHot(10);
        model.addAttribute("hotList", hotList);
        return "hot";
    }
}
