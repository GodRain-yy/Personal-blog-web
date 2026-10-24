package com.example.blog.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 评论视图对象。replies 只装一层子回复（业务上只支持两层）。
 */
@Data
public class CommentVO {

    private Long id;

    private Long articleId;

    private Long userId;

    private String authorName;

    private String authorAvatar;

    /** 一律转义输出，不做 HTML 渲染 */
    private String content;

    private Long parentId;

    private Long replyToId;

    /** 被回复者昵称，用于展示「回复 @张三」 */
    private String replyToName;

    private LocalDateTime createTime;

    private List<CommentVO> replies = new ArrayList<>();
}
