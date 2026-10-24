package com.example.blog;

import com.example.blog.common.PageResult;
import com.example.blog.dto.ArticleQuery;
import com.example.blog.dto.ArticleSaveRequest;
import com.example.blog.entity.Category;
import com.example.blog.mapper.CategoryMapper;
import com.example.blog.service.ArticleService;
import com.example.blog.vo.ArchiveGroupVO;
import com.example.blog.vo.ArticleDetailVO;
import com.example.blog.vo.ArticleVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文章链路自检：保存 → 渲染过滤 → 详情 → 列表 → 归档。
 * 文章有意保留在库中，便于启动后在浏览器查看效果。
 */
@SpringBootTest
class ArticleServiceTest {

    private static final String XSS_CONTENT = """
            # 这是一篇用于验证的文章

            <script>alert('xss')</script>

            下面是正常正文段落。

            ```java
            System.out.println("hello");
            ```

            | 列1 | 列2 |
            |----|----|
            | A  | B  |
            """;

    @Autowired
    private ArticleService articleService;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private com.example.blog.util.CacheService cacheService;

    @org.junit.jupiter.api.BeforeEach
    void clearCache() {
        // 缓存不参与测试事务回滚，先清一遍避免脏数据影响断言
        cacheService.deleteByPrefix("blog:");
    }

    @Test
    void shouldSaveArticleWithSafeHtml() {
        Category category = findOrCreateCategory();

        ArticleSaveRequest request = new ArticleSaveRequest();
        request.setTitle("XSS 过滤验证示例文章");
        request.setContent(XSS_CONTENT);
        request.setCategoryId(category.getId());
        request.setStatus(1);
        request.setIsTop(0);
        request.setIsOriginal(1);
        request.setTagNames(List.of("Java,SpringBoot"));

        Long id = articleService.saveArticle(request, 1L);
        System.out.println(">>> 示例文章已创建, id=" + id);

        ArticleDetailVO detail = articleService.getDetail(id);
        assertNotNull(detail, "文章应可查询到");
        assertFalse(detail.getContentHtml().contains("<script"), "入库 HTML 必须已过滤 script");
        assertTrue(detail.getContentHtml().contains("<h1>"), "标题应正常渲染");
        assertTrue(detail.getContentHtml().contains("<table>"), "表格应正常渲染");
        assertTrue(detail.getSummary() != null && !detail.getSummary().isBlank(), "摘要应自动生成");
        assertTrue(detail.getWordCount() > 0, "字数应统计出来");
        assertEquals(2, detail.getTagList().size(), "逗号分隔的标签应被拆成两个");
        assertEquals(category.getName(), detail.getCategoryName(), "分类名应正确联表查出");

        ArticleQuery query = new ArticleQuery();
        query.setStatus(1);
        PageResult<ArticleVO> page = articleService.listArticles(query);
        assertTrue(page.getTotal() >= 1, "列表应能查到已发布文章");
        assertFalse(page.getRecords().isEmpty());

        List<ArchiveGroupVO> groups = articleService.getArchive();
        assertFalse(groups.isEmpty(), "归档应至少有一个分组");
        assertFalse(groups.get(0).getArticles().isEmpty(), "归档分组内应有文章");
    }

    private Category findOrCreateCategory() {
        Category existing = categoryMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Category>()
                        .eq(Category::getName, "技术笔记"));
        if (existing != null) {
            return existing;
        }
        Category category = new Category();
        category.setName("技术笔记");
        category.setSort(1);
        categoryMapper.insert(category);
        return category;
    }
}
