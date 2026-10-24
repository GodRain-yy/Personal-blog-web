package com.example.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 后台文章保存入参。
 */
@Data
public class ArticleSaveRequest {

    private Long id;

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题最长 200 个字符")
    private String title;

    @Size(max = 500, message = "摘要最长 500 个字符")
    private String summary;

    /** Markdown 原文 */
    private String content;

    private Long categoryId;

    private String cover;

    /** 0草稿 1发布 */
    private Integer status;

    private Integer isTop;

    private Integer isOriginal;

    /** 标签名列表，不存在的会自动创建 */
    private List<String> tagNames;
}
