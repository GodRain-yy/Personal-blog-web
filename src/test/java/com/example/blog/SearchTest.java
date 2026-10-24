package com.example.blog;

import com.example.blog.common.PageResult;
import com.example.blog.dto.ArticleQuery;
import com.example.blog.dto.ArticleSaveRequest;
import com.example.blog.service.ArticleService;
import com.example.blog.vo.ArticleVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 全文搜索自检。
 * 注意：InnoDB 的全文索引在事务提交后才刷新，所以这里不能加 @Transactional，
 * 测试自己创建数据、自己清理。
 */
@SpringBootTest
class SearchTest {

    private static final String UNIQUE_TITLE = "麒麟芯片架构解析";
    private static final String UNIQUE_SUMMARY = "这是一篇关于麒麟处理器微架构的分析文章";

    @Autowired
    private ArticleService articleService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private com.example.blog.util.CacheService cacheService;

    private Long createdId;

    @AfterEach
    void cleanUp() {
        if (createdId != null) {
            articleService.deleteArticle(createdId);
            createdId = null;
        }
        // 列表缓存不随事务回滚，测完清掉，避免影响后续测试
        cacheService.deleteByPrefix("blog:");
    }

    @Test
    void shouldHitChineseKeywordViaFulltextIndex() {
        createdId = createArticle();

        // 标题里的中文词
        assertTrue(search("麒麟").getTotal() >= 1, "标题中的中文关键词应能命中");
        // 摘要里的中文词
        assertTrue(search("微架构").getTotal() >= 1, "摘要中的中文关键词应能命中");

        // 确认真的走了 ft_title_summary 全文索引，而不是全表扫描
        ExplainPlan plan = explain("麒麟");
        System.out.println(">>> EXPLAIN: " + plan);
        assertEquals("fulltext", plan.type, "搜索应走全文索引，实际 type=" + plan.type);
        assertEquals("ft_title_summary", plan.key, "应命中 ft_title_summary，实际 key=" + plan.key);
    }

    @Test
    void singleCharKeywordShouldFallbackToLike() {
        createdId = createArticle();

        PageResult<ArticleVO> result = search("芯");
        assertTrue(result.getTotal() >= 1, "单字符无法构成 2-gram，应降级为 LIKE 并命中");
    }

    @Test
    void shouldReturnEmptyForNonExistKeyword() {
        createdId = createArticle();
        PageResult<ArticleVO> result = search("绝不可能存在的词zyxwvu");
        assertTrue(result.getRecords().isEmpty(), "不存在的关键词应返回空结果");
    }

    private Long createArticle() {
        ArticleSaveRequest request = new ArticleSaveRequest();
        request.setTitle(UNIQUE_TITLE);
        request.setContent("## 概述\n\n" + UNIQUE_SUMMARY + "\n\n正文段落。");
        request.setSummary(UNIQUE_SUMMARY);
        request.setStatus(1);
        request.setIsTop(0);
        request.setIsOriginal(1);
        request.setTagNames(List.of("芯片"));
        return articleService.saveArticle(request, 1L);
    }

    private PageResult<ArticleVO> search(String keyword) {
        ArticleQuery query = new ArticleQuery();
        query.setStatus(1);
        query.setKeyword(keyword);
        return articleService.listArticles(query);
    }

    /**
     * MySQL 9 的 EXPLAIN 默认输出 TREE 格式（单列文本），这里显式指定 TRADITIONAL 以便读 type/key。
     */
    private ExplainPlan explain(String keyword) {
        String sql = "EXPLAIN FORMAT=TRADITIONAL SELECT a.id FROM article a WHERE a.status = 1 "
                + "AND MATCH(a.title, a.summary) AGAINST (? IN BOOLEAN MODE)";
        Map<String, Object> row = jdbcTemplate.queryForMap(sql, keyword);
        return new ExplainPlan(String.valueOf(row.get("type")), String.valueOf(row.get("key")));
    }

    private record ExplainPlan(String type, String key) {
        @Override
        public String toString() {
            return "type=" + type + ", key=" + key;
        }
    }
}
