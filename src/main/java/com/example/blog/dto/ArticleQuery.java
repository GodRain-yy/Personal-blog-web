package com.example.blog.dto;

import lombok.Data;

/**
 * 文章查询条件。参数一律封装成对象，Controller 层可直接 @ModelAttribute 绑定，
 * 将来开放 REST 接口时无需改动 Service 签名。
 */
@Data
public class ArticleQuery {

    private Integer page = 1;

    private Integer size = 10;

    /** 标题/摘要关键字 */
    private String keyword;

    private Long categoryId;

    private Long tagId;

    /** 归档筛选，形如 2026-09 */
    private String yearMonth;

    /** 0草稿 1发布 2回收站；null 表示不限。由调用方显式指定：前台首页设为 1，后台列表不限 */
    private Integer status;

    public Integer getPage() {
        return (page == null || page < 1) ? 1 : page;
    }

    public Integer getSize() {
        if (size == null || size < 1) {
            return 10;
        }
        return Math.min(size, 50);
    }
}
