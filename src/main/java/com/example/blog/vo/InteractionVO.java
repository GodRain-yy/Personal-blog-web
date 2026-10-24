package com.example.blog.vo;

import lombok.Data;

/**
 * 当前用户对某篇文章的交互状态 + 点赞总数。
 * liked/favorited 是「人-文」维度，前端据此高亮按钮。
 */
@Data
public class InteractionVO {

    /** 是否已点赞 */
    private boolean liked;

    /** 是否已收藏 */
    private boolean favorited;

    /** 文章点赞总数 */
    private Integer likeCount;
}
