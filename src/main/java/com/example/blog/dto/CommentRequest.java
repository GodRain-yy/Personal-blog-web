package com.example.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CommentRequest {

    @NotNull(message = "文章 ID 不能为空")
    private Long articleId;

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 500, message = "评论最长 500 个字符")
    private String content;

    /** 0 表示顶级评论 */
    private Long parentId;

    /** 被回复的评论 ID，顶级评论为 null */
    private Long replyToId;
}
