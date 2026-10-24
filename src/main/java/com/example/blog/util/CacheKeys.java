package com.example.blog.util;

import com.example.blog.dto.ArticleQuery;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 缓存 key 统一在这里定义，避免各处硬编码字符串拼错。
 */
public final class CacheKeys {

    private CacheKeys() {
    }

    /** 文章详情 */
    public static final String ARTICLE_DETAIL = "blog:article:detail:";

    /** 文章列表（后面接查询参数的 MD5） */
    public static final String ARTICLE_LIST = "blog:article:list:";

    /** 归档（全量，按年月分组） */
    public static final String ARCHIVE_ALL = "blog:archive:all";

    /** 侧边栏分类 */
    public static final String CATEGORY_ALL = "blog:category:all";

    /** 侧边栏标签 */
    public static final String TAG_ALL = "blog:tag:all";

    /** 浏览量增量（Redis 累积，定时回刷 DB）。存的是「相对基数的增量」，不是绝对值 */
    public static final String ARTICLE_VIEW_INCR = "blog:article:viewincr:";

    /** 热门榜 ZSet：member=文章ID，score=热度（浏览*1 + 点赞*5 + 评论*10） */
    public static final String ARTICLE_HOT = "blog:article:hot";

    /** 缓存空值占位，防止缓存穿透打到 DB */
    public static final String NULL_PLACEHOLDER = "__NULL__";

    public static String articleViewIncr(Long id) {
        return ARTICLE_VIEW_INCR + id;
    }

    public static String articleDetail(Long id) {
        return ARTICLE_DETAIL + id;
    }

    /**
     * 列表 key 必须包含全部查询维度，否则不同筛选条件会串数据（原方案第 3 号缺陷）。
     * 参数较多，用 MD5 压缩成定长串。
     */
    public static String articleList(ArticleQuery query) {
        String raw = String.join("|",
                String.valueOf(query.getPage()),
                String.valueOf(query.getSize()),
                String.valueOf(query.getStatus()),
                String.valueOf(query.getKeyword()),
                String.valueOf(query.getCategoryId()),
                String.valueOf(query.getTagId()),
                String.valueOf(query.getYearMonth()));
        return ARTICLE_LIST + md5(raw);
    }

    private static String md5(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // MD5 一定存在，兜底用哈希码，不会真的走到这里
            return String.valueOf(input.hashCode());
        }
    }
}
