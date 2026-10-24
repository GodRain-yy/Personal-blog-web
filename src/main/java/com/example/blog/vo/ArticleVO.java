package com.example.blog.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 列表页/搜索结果页的文章条目。
 * 与 Article 实体分离：前台不需要 content、content_html 等重字段。
 */
@Data
public class ArticleVO {

    private Long id;

    private String title;

    private String summary;

    private String cover;

    private LocalDateTime publishTime;

    private Integer viewCount;

    private Integer commentCount;

    private Integer wordCount;

    private String categoryName;

    private String authorName;

    /** 逗号分隔的标签串，列表页直接用；详情页另有结构化 tagList */
    private String tagNames;
}
