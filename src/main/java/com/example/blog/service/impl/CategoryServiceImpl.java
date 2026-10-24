package com.example.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.example.blog.common.BusinessException;
import com.example.blog.common.ErrorCode;
import com.example.blog.entity.Category;
import com.example.blog.mapper.ArticleMapper;
import com.example.blog.mapper.CategoryMapper;
import com.example.blog.service.CategoryService;
import com.example.blog.util.CacheKeys;
import com.example.blog.util.CacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {

    private final ArticleMapper articleMapper;

    private final CacheService cacheService;

    /** 侧边栏每个页面都要读，缓存 2 小时，变更时主动失效 */
    private static final Duration LIST_TTL = Duration.ofHours(2);

    @Override
    @SuppressWarnings("unchecked")
    public List<Category> listAll() {
        Optional<Object> cached = cacheService.get(CacheKeys.CATEGORY_ALL);
        if (cached.isPresent()) {
            return (List<Category>) cached.get();
        }
        List<Category> categories = list(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getSort)
                .orderByAsc(Category::getId));
        cacheService.set(CacheKeys.CATEGORY_ALL, categories, LIST_TTL, 600);
        return categories;
    }

    @Override
    public Long saveCategory(Long id, String name, Integer sort) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "分类名不能为空");
        }
        if (trimmed.length() > 50) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "分类名最长 50 个字符");
        }

        LambdaQueryWrapper<Category> duplicate = new LambdaQueryWrapper<Category>().eq(Category::getName, trimmed);
        if (id != null) {
            duplicate.ne(Category::getId, id);
        }
        if (count(duplicate) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "分类名已存在");
        }

        Category category = (id == null) ? new Category() : getById(id);
        if (category == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "分类不存在");
        }
        category.setName(trimmed);
        category.setSort(sort == null ? 0 : sort);
        saveOrUpdate(category);
        cacheService.deleteAfterCommit(CacheKeys.CATEGORY_ALL);
        return category.getId();
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        if (getById(id) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "分类不存在");
        }
        // 先把文章从该分类摘出来，避免留下指向已删除分类的孤儿外键
        articleMapper.clearCategory(id);
        removeById(id);
        cacheService.deleteAfterCommit(CacheKeys.CATEGORY_ALL);
        // 文章的分类名变了，文章列表与详情缓存也要失效
        cacheService.deleteByPrefixAfterCommit(CacheKeys.ARTICLE_LIST);
        cacheService.deleteByPrefixAfterCommit(CacheKeys.ARTICLE_DETAIL);
    }

    @Override
    public long countArticles(Long categoryId) {
        return articleMapper.countByCategoryId(categoryId);
    }
}
