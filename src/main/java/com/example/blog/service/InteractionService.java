package com.example.blog.service;

import com.example.blog.vo.InteractionVO;

/**
 * 点赞 / 收藏交互。
 * 设计约束：
 * 1. 必须登录（未登录走 /api/** 的 401 拦截）
 * 2. 幂等切换：重复点击同一动作只改变一次状态
 * 3. 点赞数变动同步维护 article.like_count 冗余列与热门榜 ZSet
 */
public interface InteractionService {

    /** 切换点赞，返回最新状态与点赞总数 */
    InteractionVO toggleLike(Long userId, Long articleId);

    /** 切换收藏，返回最新状态 */
    InteractionVO toggleFavorite(Long userId, Long articleId);

    /** 查询当前用户对文章的点赞/收藏状态（匿名用户返回全 false） */
    InteractionVO getStatus(Long userId, Long articleId);
}
