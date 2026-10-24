package com.example.blog.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 热门榜条目：仅含卡片展示所需字段。
 */
@Data
public class HotItemVO {

    private Long id;

    private String title;

    private String summary;

    private String cover;

    private Integer viewCount;

    private Integer likeCount;

    private Integer commentCount;

    private LocalDateTime publishTime;
}
