package com.example.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("article")
public class Article {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long categoryId;

    private String title;

    private String summary;

    /** Markdown 原文 */
    private String content;

    /** 渲染并过白名单后的 HTML */
    private String contentHtml;

    private String cover;

    /** 0草稿 1发布 2回收站 */
    private Integer status;

    private Integer isTop;

    private Integer isOriginal;

    private Integer viewCount;

    private Integer likeCount;

    private Integer commentCount;

    private Integer wordCount;

    private LocalDateTime publishTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
