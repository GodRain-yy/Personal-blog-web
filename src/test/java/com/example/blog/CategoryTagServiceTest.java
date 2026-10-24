package com.example.blog;

import com.example.blog.common.BusinessException;
import com.example.blog.dto.ArticleSaveRequest;
import com.example.blog.entity.Article;
import com.example.blog.entity.Category;
import com.example.blog.entity.Tag;
import com.example.blog.mapper.ArticleMapper;
import com.example.blog.service.ArticleService;
import com.example.blog.service.CategoryService;
import com.example.blog.service.TagService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分类与标签管理自检：重名拦截、删除清理关联、长度校验。
 */
@SpringBootTest
@Transactional
class CategoryTagServiceTest {

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private TagService tagService;

    @Autowired
    private ArticleService articleService;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private com.example.blog.util.CacheService cacheService;

    @org.junit.jupiter.api.BeforeEach
    void clearCache() {
        // 本类用 @Transactional 回滚，但 Redis 不会回滚，先清一遍
        cacheService.deleteByPrefix("blog:");
    }

    @Test
    void shouldRejectDuplicateCategoryName() {
        Long id = categoryService.saveCategory(null, "单元测试分类", 5);
        assertNotNull(id);

        assertThrows(BusinessException.class, () -> categoryService.saveCategory(null, "单元测试分类", 6));
        // 改自己但名字不变，应允许
        assertEquals(id, categoryService.saveCategory(id, "单元测试分类", 7));
    }

    @Test
    void shouldRejectBlankOrTooLongName() {
        assertThrows(BusinessException.class, () -> categoryService.saveCategory(null, "   ", 0));
        assertThrows(BusinessException.class, () -> tagService.saveTag(null, ""));
        assertThrows(BusinessException.class, () -> categoryService.saveCategory(null, "x".repeat(51), 0));
        assertThrows(BusinessException.class, () -> tagService.saveTag(null, "x".repeat(51)));
    }

    @Test
    void deletingCategoryShouldDetachArticles() {
        Long categoryId = categoryService.saveCategory(null, "待删除分类", 1);

        ArticleSaveRequest request = new ArticleSaveRequest();
        request.setTitle("挂在待删除分类下的文章");
        request.setContent("正文");
        request.setCategoryId(categoryId);
        request.setStatus(1);
        Long articleId = articleService.saveArticle(request, 1L);

        assertEquals(1, categoryService.countArticles(categoryId));

        categoryService.deleteCategory(categoryId);

        Article article = articleMapper.selectById(articleId);
        assertNull(article.getCategoryId(), "删除分类后文章的分类应被置空，避免孤儿外键");
        assertEquals(0, categoryService.countArticles(categoryId));
    }

    @Test
    void deletingTagShouldCleanRelations() {
        Long tagId = tagService.saveTag(null, "待删除标签");

        ArticleSaveRequest request = new ArticleSaveRequest();
        request.setTitle("带标签的文章");
        request.setContent("正文");
        request.setStatus(1);
        request.setTagNames(java.util.List.of("待删除标签"));
        Long articleId = articleService.saveArticle(request, 1L);

        assertTrue(articleMapper.selectTagIdsByArticleId(articleId).contains(tagId), "标签关联应已建立");

        tagService.deleteTag(tagId);

        assertTrue(articleMapper.selectTagIdsByArticleId(articleId).isEmpty(), "删除标签后关联应被清理");
    }

    @Test
    void listShouldBeOrdered() {
        categoryService.saveCategory(null, "排序分类B", 2);
        categoryService.saveCategory(null, "排序分类A", 1);

        var categories = categoryService.listAll();
        String firstName = categories.stream()
                .filter(c -> c.getName().startsWith("排序分类"))
                .map(Category::getName)
                .findFirst()
                .orElse(null);
        assertEquals("排序分类A", firstName, "sort 小的应排在前面");

        var tags = tagService.listAll();
        assertTrue(tags.stream().anyMatch(t -> t.getName() != null), "标签列表应可正常返回");
    }

    @Test
    void deletingNonExistShouldThrow() {
        assertThrows(BusinessException.class, () -> categoryService.deleteCategory(-1L));
        assertThrows(BusinessException.class, () -> tagService.deleteTag(-1L));
    }
}
