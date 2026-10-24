package com.example.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.example.blog.common.BusinessException;
import com.example.blog.common.ErrorCode;
import com.example.blog.entity.Tag;
import com.example.blog.mapper.ArticleMapper;
import com.example.blog.mapper.TagMapper;
import com.example.blog.service.TagService;
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
public class TagServiceImpl extends ServiceImpl<TagMapper, Tag> implements TagService {

    private final ArticleMapper articleMapper;

    private final CacheService cacheService;

    private static final Duration LIST_TTL = Duration.ofHours(2);

    @Override
    @SuppressWarnings("unchecked")
    public List<Tag> listAll() {
        Optional<Object> cached = cacheService.get(CacheKeys.TAG_ALL);
        if (cached.isPresent()) {
            return (List<Tag>) cached.get();
        }
        List<Tag> tags = list(new LambdaQueryWrapper<Tag>().orderByAsc(Tag::getId));
        cacheService.set(CacheKeys.TAG_ALL, tags, LIST_TTL, 600);
        return tags;
    }

    @Override
    public Long saveTag(Long id, String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "标签名不能为空");
        }
        if (trimmed.length() > 50) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "标签名最长 50 个字符");
        }

        LambdaQueryWrapper<Tag> duplicate = new LambdaQueryWrapper<Tag>().eq(Tag::getName, trimmed);
        if (id != null) {
            duplicate.ne(Tag::getId, id);
        }
        if (count(duplicate) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "标签名已存在");
        }

        Tag tag = (id == null) ? new Tag() : getById(id);
        if (tag == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "标签不存在");
        }
        tag.setName(trimmed);
        saveOrUpdate(tag);
        cacheService.deleteAfterCommit(CacheKeys.TAG_ALL);
        return tag.getId();
    }

    @Override
    @Transactional
    public void deleteTag(Long id) {
        if (getById(id) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "标签不存在");
        }
        articleMapper.deleteTagsByTagId(id);
        removeById(id);
        cacheService.deleteAfterCommit(CacheKeys.TAG_ALL);
        // 文章上的标签串变了，列表与详情缓存同样要失效
        cacheService.deleteByPrefixAfterCommit(CacheKeys.ARTICLE_LIST);
        cacheService.deleteByPrefixAfterCommit(CacheKeys.ARTICLE_DETAIL);
    }
}
