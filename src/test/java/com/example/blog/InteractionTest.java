package com.example.blog;

import com.example.blog.common.Result;
import com.example.blog.entity.Article;
import com.example.blog.entity.UserLike;
import com.example.blog.mapper.ArticleFavoriteMapper;
import com.example.blog.mapper.ArticleMapper;
import com.example.blog.mapper.UserLikeMapper;
import com.example.blog.service.InteractionService;
import com.example.blog.service.ViewCountFlushService;
import com.example.blog.util.CacheKeys;
import com.example.blog.util.CacheService;
import com.example.blog.vo.InteractionVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("dev")
class InteractionTest {

    @Autowired
    private InteractionService interactionService;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private UserLikeMapper userLikeMapper;

    @Autowired
    private ArticleFavoriteMapper favoriteMapper;

    @Autowired
    private ViewCountFlushService viewCountFlushService;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long articleId;

    @AfterEach
    void cleanUp() {
        if (articleId != null) {
            userLikeMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserLike>()
                    .eq(UserLike::getArticleId, articleId));
            favoriteMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.example.blog.entity.ArticleFavorite>()
                    .eq(com.example.blog.entity.ArticleFavorite::getArticleId, articleId));
            articleMapper.deleteById(articleId);
            cacheService.deleteViewIncr(articleId);
            articleId = null;
        }
    }

    private Long prepareArticle() {
        Article a = new Article();
        a.setUserId(1L);
        a.setTitle("交互测试文章");
        a.setContent("x");
        a.setContentHtml("<p>x</p>");
        a.setStatus(1);
        a.setViewCount(0);
        a.setLikeCount(0);
        a.setCommentCount(0);
        a.setWordCount(1);
        articleMapper.insert(a);
        return a.getId();
    }

    @Test
    void likeToggle_twice_returns_to_original() {
        articleId = prepareArticle();
        Long userId = 2L;

        InteractionVO first = interactionService.toggleLike(userId, articleId);
        assertTrue(first.isLiked(), "第一次点赞应为已赞");
        assertEquals(1, first.getLikeCount(), "点赞数应为 1");

        InteractionVO second = interactionService.toggleLike(userId, articleId);
        assertFalse(second.isLiked(), "第二次应为取消赞");
        assertEquals(0, second.getLikeCount(), "取消后点赞数回到 0");

        // 再 toggle 一次应重新点赞（不是「取消」，点赞按钮就是开关）
        InteractionVO third = interactionService.toggleLike(userId, articleId);
        assertTrue(third.isLiked(), "第三次应重新点赞");
        assertEquals(1, third.getLikeCount(), "重新点赞后计数回到 1");
    }

    @Test
    void favoriteToggle_works() {
        articleId = prepareArticle();
        InteractionVO first = interactionService.toggleFavorite(3L, articleId);
        assertTrue(first.isFavorited(), "第一次收藏应为已收藏");
        InteractionVO second = interactionService.toggleFavorite(3L, articleId);
        assertFalse(second.isFavorited(), "第二次应为取消收藏");
    }

    @Test
    void getStatus_anonymous_returns_false() {
        articleId = prepareArticle();
        InteractionVO status = interactionService.getStatus(null, articleId);
        assertFalse(status.isLiked());
        assertFalse(status.isFavorited());
    }

    @Test
    void hotRanking_updates_on_like() {
        articleId = prepareArticle();
        interactionService.toggleLike(5L, articleId);
        // 点赞应让热门榜 ZSet 出现该文章（score = LIKE_WEIGHT = 5）
        assertTrue(cacheService.zrevrangeHot(0, 9).contains(articleId),
                "点赞后热门榜应包含该文章");
    }

    @Test
    void viewCountFlush_writes_back_to_db() {
        articleId = prepareArticle();
        int before = articleMapper.selectById(articleId).getViewCount();

        // 模拟 3 次访问，每次只改 Redis 增量，不写 DB
        cacheService.incrementView(articleId);
        cacheService.incrementView(articleId);
        cacheService.incrementView(articleId);
        assertEquals(before, articleMapper.selectById(articleId).getViewCount(),
                "访问期间 DB 浏览量不应被改动");

        // 手动触发回刷（生产中由 @Scheduled 每 60s 执行）
        viewCountFlushService.flush();

        int after = articleMapper.selectById(articleId).getViewCount();
        assertEquals(before + 3, after, "回刷后 DB 应增加 3 次浏览");
        assertEquals(0, cacheService.getViewIncr(articleId), "回刷后增量 key 应清零");
    }
}
