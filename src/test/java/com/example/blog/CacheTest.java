package com.example.blog;

import com.example.blog.common.PageResult;
import com.example.blog.dto.ArticleQuery;
import com.example.blog.dto.ArticleSaveRequest;
import com.example.blog.service.ArticleService;
import com.example.blog.service.CategoryService;
import com.example.blog.util.CacheKeys;
import com.example.blog.util.CacheService;
import com.example.blog.vo.ArticleDetailVO;
import com.example.blog.vo.ArticleVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 缓存自检。判据不是「代码写了缓存」，而是「缓存真的挡住了数据库、变更真的让缓存失效」。
 * 手法：绕过 Service 直接改库，再看 Service 读到的值 —— 读到旧值说明命中缓存。
 */
@SpringBootTest
class CacheTest {

    @Autowired
    private ArticleService articleService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long articleId;

    @BeforeEach
    void setUp() {
        cacheService.deleteByPrefix("blog:");
        ArticleSaveRequest request = new ArticleSaveRequest();
        request.setTitle("缓存测试原文标题");
        request.setContent("缓存测试正文");
        request.setStatus(1);
        request.setIsTop(0);
        request.setIsOriginal(1);
        articleId = articleService.saveArticle(request, 1L);
    }

    @AfterEach
    void cleanUp() {
        articleService.deleteArticle(articleId);
        cacheService.deleteByPrefix("blog:");
    }

    @Test
    void detailShouldBeServedFromCacheUntilEvicted() {
        String key = CacheKeys.articleDetail(articleId);

        assertEquals("缓存测试原文标题", articleService.getDetail(articleId).getTitle());
        assertTrue(cacheService.exists(key), "首次查询后应写入详情缓存");

        // 绕过 Service 直接改库，缓存里还是旧值
        jdbcTemplate.update("UPDATE article SET title = ? WHERE id = ?", "直接改库的标题", articleId);
        assertEquals("缓存测试原文标题", articleService.getDetail(articleId).getTitle(),
                "缓存未失效前应读到旧标题，说明确实命中了缓存");
        assertNotEquals("直接改库的标题", articleService.getDetail(articleId).getTitle());

        // 清掉缓存后应读到新值
        cacheService.delete(key);
        assertEquals("直接改库的标题", articleService.getDetail(articleId).getTitle(),
                "清缓存后应重新查库读到新标题");
    }

    @Test
    void missingArticleShouldBeNullCached() {
        String key = CacheKeys.articleDetail(999999L);

        assertNull(articleService.getDetail(999999L), "不存在的文章应返回 null");
        assertTrue(cacheService.exists(key), "空结果也要写占位，防止缓存穿透");
        // 再查一次不应报错，且占位 TTL 明显短于正常缓存
        assertNull(articleService.getDetail(999999L));
        long ttl = cacheService.ttlSeconds(key);
        assertTrue(ttl > 0 && ttl <= 120, "空值占位 TTL 应在 2 分钟内，实际 " + ttl + " 秒");
    }

    @Test
    void savingArticleShouldEvictDetailAndList() {
        String detailKey = CacheKeys.articleDetail(articleId);
        assertEquals("缓存测试原文标题", articleService.getDetail(articleId).getTitle());
        assertTrue(cacheService.exists(detailKey));

        ArticleQuery query = new ArticleQuery();
        query.setStatus(1);
        articleService.listArticles(query);
        assertTrue(cacheService.exists(CacheKeys.articleList(query)), "列表查询后应写入列表缓存");

        // 走 Service 保存，事务提交后缓存应被清掉
        ArticleSaveRequest request = new ArticleSaveRequest();
        request.setId(articleId);
        request.setTitle("通过 Service 改过的新标题");
        request.setContent("新正文");
        request.setStatus(1);
        articleService.saveArticle(request, 1L);

        assertFalse(cacheService.exists(detailKey), "保存后详情缓存应被删除");
        assertFalse(cacheService.exists(CacheKeys.articleList(query)), "保存后列表缓存应被删除");
        assertEquals("通过 Service 改过的新标题", articleService.getDetail(articleId).getTitle(),
                "保存后应能读到新标题");
    }

    @Test
    void deletingArticleShouldEvictCache() {
        String detailKey = CacheKeys.articleDetail(articleId);
        articleService.getDetail(articleId);
        assertTrue(cacheService.exists(detailKey));

        articleService.deleteArticle(articleId);

        assertFalse(cacheService.exists(detailKey), "删除后详情缓存应被清掉");
        assertNull(articleService.getDetail(articleId), "删除后应查不到文章");

        // cleanUp 会再删一次，这里换个 id 占位避免报错
        ArticleSaveRequest request = new ArticleSaveRequest();
        request.setTitle("缓存测试原文标题");
        request.setContent("正文");
        request.setStatus(1);
        articleId = articleService.saveArticle(request, 1L);
    }

    @Test
    void listCacheKeyShouldVaryByQuery() {
        ArticleQuery base = new ArticleQuery();
        base.setStatus(1);

        ArticleQuery withKeyword = new ArticleQuery();
        withKeyword.setStatus(1);
        withKeyword.setKeyword("缓存");

        ArticleQuery withCategory = new ArticleQuery();
        withCategory.setStatus(1);
        withCategory.setCategoryId(1L);

        assertNotEquals(CacheKeys.articleList(base), CacheKeys.articleList(withKeyword),
                "不同关键词不能用同一个列表 key，否则会串数据");
        assertNotEquals(CacheKeys.articleList(base), CacheKeys.articleList(withCategory),
                "不同分类不能用同一个列表 key");

        PageResult<ArticleVO> all = articleService.listArticles(base);
        PageResult<ArticleVO> hit = articleService.listArticles(withKeyword);
        assertTrue(all.getTotal() >= 1);
        assertTrue(hit.getRecords().stream().anyMatch(a -> a.getTitle().contains("缓存")),
                "关键词查询应命中对应文章");
    }

    @Test
    void categoryCacheShouldInvalidateOnChange() {
        var first = categoryService.listAll();
        assertTrue(cacheService.exists(CacheKeys.CATEGORY_ALL), "分类列表应被缓存");

        // 绕过 Service 直接改库：MySQL 不允许 UPDATE 子查询引用同表，先查出 id 再改
        Long minId = jdbcTemplate.queryForObject("SELECT MIN(id) FROM category", Long.class);
        String oldName = jdbcTemplate.queryForObject("SELECT name FROM category WHERE id = ?", String.class, minId);
        jdbcTemplate.update("UPDATE category SET name = ? WHERE id = ?", oldName + "_改", minId);
        try {
            assertTrue(categoryService.listAll().stream().anyMatch(c -> oldName.equals(c.getName())),
                    "缓存未失效时读到的应还是旧名称快照");

            // 走 Service 保存触发失效
            String newName = "缓存失效验证分类" + System.currentTimeMillis();
            categoryService.saveCategory(null, newName, 99);
            assertFalse(cacheService.exists(CacheKeys.CATEGORY_ALL), "分类变更应清掉分类缓存");
            assertTrue(categoryService.listAll().stream().anyMatch(c -> newName.equals(c.getName())),
                    "清缓存后应能读到新分类");
        } finally {
            jdbcTemplate.update("UPDATE category SET name = ? WHERE id = ?", oldName, minId);
            cacheService.delete(CacheKeys.CATEGORY_ALL);
        }
    }

    @Test
    void detailTtlShouldBeRandomized() {
        ArticleSaveRequest request = new ArticleSaveRequest();
        request.setTitle("TTL 抖动测试文章");
        request.setContent("正文");
        request.setStatus(1);
        Long secondId = articleService.saveArticle(request, 1L);
        try {
            articleService.getDetail(articleId);
            articleService.getDetail(secondId);

            long ttl1 = cacheService.ttlSeconds(CacheKeys.articleDetail(articleId));
            long ttl2 = cacheService.ttlSeconds(CacheKeys.articleDetail(secondId));
            // 30 分钟基础 + 0~10 分钟抖动
            assertTrue(ttl1 >= 1800 && ttl1 <= 2400, "详情 TTL 应在 30~40 分钟之间，实际 " + ttl1);
            assertTrue(ttl2 >= 1800 && ttl2 <= 2400, "详情 TTL 应在 30~40 分钟之间，实际 " + ttl2);
            cacheService.deleteByPrefix(CacheKeys.ARTICLE_DETAIL);
        } finally {
            articleService.deleteArticle(secondId);
        }
    }
}
