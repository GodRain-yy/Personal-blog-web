package com.example.blog.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 详情页视图对象：在列表项基础上补齐正文 HTML 与结构化标签。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ArticleDetailVO extends ArticleVO {

    /** 已过白名单的正文 HTML，Thymeleaf 用 th:utext 输出 */
    private String contentHtml;

    private List<String> tagList;

    /** 点赞总数（article.like_count 冗余列） */
    private Integer likeCount;
}
