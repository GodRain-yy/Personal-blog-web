package com.example.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.example.blog.common.BusinessException;
import com.example.blog.common.ErrorCode;
import com.example.blog.common.PageResult;
import com.example.blog.dto.ArticleQuery;
import com.example.blog.dto.ArticleSaveRequest;
import com.example.blog.entity.Article;
import com.example.blog.entity.Tag;
import com.example.blog.mapper.ArticleMapper;
import com.example.blog.mapper.CategoryMapper;
import com.example.blog.mapper.TagMapper;
import com.example.blog.service.ArticleService;
import com.example.blog.util.CacheKeys;
import com.example.blog.util.CacheService;
import com.example.blog.util.MarkdownUtil;
import com.example.blog.vo.ArchiveGroupVO;
import com.example.blog.vo.ArchiveItemVO;
import com.example.blog.vo.ArticleDetailVO;
import com.example.blog.vo.ArticleVO;
import com.example.blog.vo.HotItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article> implements ArticleService {

    private static final int SUMMARY_LENGTH = 120;

    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyy-MM");

    /** 详情缓存 30 分钟，外加 0~10 分钟随机抖动，避免同时失效造成雪崩 */
    private static final Duration DETAIL_TTL = Duration.ofMinutes(30);
    private static final long DETAIL_JITTER_SECONDS = 600;

    /** 列表缓存 5 分钟 + 0~2 分钟抖动：列表变更比详情频繁，TTL 要短 */
    private static final Duration LIST_TTL = Duration.ofMinutes(5);
    private static final long LIST_JITTER_SECONDS = 120;

    /** 空值占位 2 分钟：挡住不存在的 id 被反复打库，又不会长时间掩盖新发布的文章 */
    private static final Duration NULL_TTL = Duration.ofMinutes(2);

    private final CategoryMapper categoryMapper;

    private final TagMapper tagMapper;

    private final CacheService cacheService;

    @Override
    @SuppressWarnings("unchecked")
    public PageResult<ArticleVO> listArticles(ArticleQuery query) {
        if (query == null) {
            query = new ArticleQuery();
        }
        String key = CacheKeys.articleList(query);
        Optional<Object> cached = cacheService.get(key);
        if (cached.isPresent()) {
            return (PageResult<ArticleVO>) cached.get();
        }

        Page<ArticleVO> page = new Page<>(query.getPage(), query.getSize());
        List<ArticleVO> records = baseMapper.selectArticleList(page, query);
        page.setRecords(records == null ? List.of() : records);
        PageResult<ArticleVO> result = PageResult.of(page);
        cacheService.set(key, result, LIST_TTL, LIST_JITTER_SECONDS);
        return result;
    }

    @Override
    public ArticleDetailVO getDetail(Long id) {
        if (id == null || id <= 0) {
            return null;
        }

        String key = CacheKeys.articleDetail(id);
        Optional<Object> cached = cacheService.get(key);
        if (cached.isPresent()) {
            // 空值占位也算命中，直接返回 null，不再打数据库
            if (cacheService.isNullPlaceholder(cached.get())) {
                return null;
            }
            ArticleDetailVO vo = (ArticleDetailVO) cached.get();
            // 详情缓存里的是「打库那一刻的基数」；真正显示的浏览数 = 基数 + Redis 累积增量
            long incr = cacheService.incrementView(id);
            cacheService.zincrHot(id, 1);
            long base = vo.getViewCount() == null ? 0 : vo.getViewCount();
            vo.setViewCount((int) (base + incr));
            return vo;
        }

        ArticleDetailVO vo = loadDetail(id);
        if (vo == null) {
            cacheService.setNull(key, NULL_TTL);
            return null;
        }
        // 首页落地：基数（DB）+ 本次增量
        long incr = cacheService.incrementView(id);
        cacheService.zincrHot(id, 1);
        long base = vo.getViewCount() == null ? 0 : vo.getViewCount();
        vo.setViewCount((int) (base + incr));
        cacheService.set(key, vo, DETAIL_TTL, DETAIL_JITTER_SECONDS);
        return vo;
    }

    private ArticleDetailVO loadDetail(Long id) {
        ArticleDetailVO vo = baseMapper.selectDetailById(id);
        if (vo == null) {
            return null;
        }
        List<Long> tagIds = baseMapper.selectTagIdsByArticleId(id);
        List<String> tagNames = new ArrayList<>();
        if (tagIds != null && !tagIds.isEmpty()) {
            tagMapper.selectByIds(tagIds).forEach(t -> tagNames.add(t.getName()));
        }
        vo.setTagList(tagNames);
        return vo;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ArchiveGroupVO> getArchive() {
        Optional<Object> cached = cacheService.get(CacheKeys.ARCHIVE_ALL);
        if (cached.isPresent()) {
            return (List<ArchiveGroupVO>) cached.get();
        }

        List<ArchiveGroupVO> groups = loadArchive();
        cacheService.set(CacheKeys.ARCHIVE_ALL, groups, DETAIL_TTL, DETAIL_JITTER_SECONDS);
        return groups;
    }

    private List<ArchiveGroupVO> loadArchive() {
        List<ArchiveItemVO> items = baseMapper.selectArchiveItems();
        List<ArchiveGroupVO> groups = new ArrayList<>();
        if (items == null || items.isEmpty()) {
            return groups;
        }
        // 已按发布时间倒序，保持插入顺序即年月倒序
        Map<String, List<ArchiveItemVO>> bucket = new LinkedHashMap<>();
        for (ArchiveItemVO item : items) {
            if (item.getPublishTime() == null) {
                continue;
            }
            bucket.computeIfAbsent(item.getPublishTime().format(YM), k -> new ArrayList<>()).add(item);
        }
        bucket.forEach((ym, list) -> {
            ArchiveGroupVO g = new ArchiveGroupVO();
            g.setYearMonth(ym);
            g.setArticles(list);
            groups.add(g);
        });
        return groups;
    }

    @Override
    public List<HotItemVO> listHot(int limit) {
        List<Long> ids = cacheService.zrevrangeHot(0, Math.max(0, limit - 1));
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Article> articles = baseMapper.selectBatchIds(ids);
        if (articles == null || articles.isEmpty()) {
            return List.of();
        }
        Map<Long, Article> byId = articles.stream().collect(Collectors.toMap(Article::getId, a -> a));
        List<HotItemVO> result = new ArrayList<>();
        for (Long id : ids) {
            Article a = byId.get(id);
            // 只展示已发布文章；草稿/回收站即使热度还在也过滤掉
            if (a == null || !Integer.valueOf(1).equals(a.getStatus())) {
                continue;
            }
            HotItemVO h = new HotItemVO();
            h.setId(a.getId());
            h.setTitle(a.getTitle());
            h.setSummary(a.getSummary());
            h.setCover(a.getCover());
            long incr = cacheService.getViewIncr(a.getId());
            h.setViewCount((a.getViewCount() == null ? 0 : a.getViewCount()) + (int) incr);
            h.setLikeCount(a.getLikeCount() == null ? 0 : a.getLikeCount());
            h.setCommentCount(a.getCommentCount() == null ? 0 : a.getCommentCount());
            h.setPublishTime(a.getPublishTime());
            result.add(h);
        }
        return result;
    }

    @Override
    @Transactional
    public Long saveArticle(ArticleSaveRequest request, Long userId) {
        if (request.getCategoryId() != null && categoryMapper.selectById(request.getCategoryId()) == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "分类不存在");
        }

        String title = request.getTitle() == null ? "" : request.getTitle().trim();
        if (title.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "标题不能为空");
        }
        if (title.length() > 200) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "标题不能超过 200 个字符");
        }
        String summary = request.getSummary() == null ? "" : request.getSummary().trim();
        if (summary.length() > 500) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "摘要不能超过 500 个字符");
        }

        String content = request.getContent() == null ? "" : request.getContent();
        if (content.length() > 200_000) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "正文内容过大");
        }
        Article article = new Article();

        boolean isUpdate = request.getId() != null;
        LocalDateTime oldPublishTime = null;
        if (isUpdate) {
            Article old = getById(request.getId());
            if (old == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "文章不存在");
            }
            oldPublishTime = old.getPublishTime();
            article.setId(old.getId());
            article.setViewCount(old.getViewCount());
            article.setLikeCount(old.getLikeCount());
            article.setCommentCount(old.getCommentCount());
            article.setUserId(old.getUserId());
        } else {
            article.setUserId(userId);
            article.setViewCount(0);
            article.setLikeCount(0);
            article.setCommentCount(0);
        }

        article.setTitle(title);
        article.setContent(content);
        // 关键：落库的是「渲染后且过白名单」的 HTML，详情页直接输出它
        article.setContentHtml(MarkdownUtil.renderToHtml(content));
        article.setSummary(resolveSummary(summary, content));
        article.setWordCount(MarkdownUtil.countWords(content));
        article.setCategoryId(request.getCategoryId());
        article.setCover(request.getCover());
        article.setStatus(request.getStatus() == null ? 0 : request.getStatus());
        article.setIsTop(request.getIsTop() == null ? 0 : request.getIsTop());
        article.setIsOriginal(request.getIsOriginal() == null ? 1 : request.getIsOriginal());

        if (Integer.valueOf(1).equals(article.getStatus())) {
            article.setPublishTime(oldPublishTime == null ? LocalDateTime.now() : oldPublishTime);
        }

        saveOrUpdate(article);

        replaceTags(article.getId(), normalizeTagNames(request.getTagNames()));
        evictAfterChange(article.getId());
        return article.getId();
    }

    @Override
    @Transactional
    public void deleteArticle(Long id) {
        if (id == null || getById(id) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文章不存在");
        }
        baseMapper.deleteTagsByArticleId(id);
        removeById(id);
        evictAfterChange(id);
    }

    /**
     * 文章变更后清缓存。
     * 必须等事务提交后再删：事务内删除的话，可能刚删完就被并发请求用旧数据回填，缓存反而更脏。
     * 列表 key 带 MD5 无法逐个枚举，按前缀扫描清理。
     */
    private void evictAfterChange(Long articleId) {
        if (articleId != null) {
            cacheService.deleteAfterCommit(CacheKeys.articleDetail(articleId));
        }
        cacheService.deleteByPrefixAfterCommit(CacheKeys.ARTICLE_LIST);
        cacheService.deleteAfterCommit(CacheKeys.ARCHIVE_ALL);
    }

    @Override
    public ArticleSaveRequest getEditForm(Long id) {
        Article article = getById(id);
        if (article == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文章不存在");
        }
        ArticleSaveRequest form = new ArticleSaveRequest();
        form.setId(article.getId());
        form.setTitle(article.getTitle());
        form.setSummary(article.getSummary());
        form.setContent(article.getContent());
        form.setCategoryId(article.getCategoryId());
        form.setCover(article.getCover());
        form.setStatus(article.getStatus());
        form.setIsTop(article.getIsTop());
        form.setIsOriginal(article.getIsOriginal());

        List<Long> tagIds = baseMapper.selectTagIdsByArticleId(id);
        List<String> names = new ArrayList<>();
        if (tagIds != null && !tagIds.isEmpty()) {
            tagMapper.selectByIds(tagIds).forEach(t -> names.add(t.getName()));
        }
        form.setTagNames(names);
        return form;
    }

    private String resolveSummary(String input, String content) {
        if (input != null && !input.isBlank()) {
            return input.trim();
        }
        return MarkdownUtil.extractSummary(content, SUMMARY_LENGTH);
    }

    /** 兼容「单个输入框逗号分隔」与「多个同名参数」两种提交方式 */
    private List<String> normalizeTagNames(List<String> raw) {
        List<String> result = new ArrayList<>();
        if (raw == null) {
            return result;
        }
        for (String item : raw) {
            if (item == null) {
                continue;
            }
            for (String part : item.split("[,，;；\\s]+")) {
                String name = part.trim();
                if (!name.isEmpty() && !result.contains(name)) {
                    result.add(name);
                }
            }
        }
        return result;
    }

    private void replaceTags(Long articleId, List<String> tagNames) {
        baseMapper.deleteTagsByArticleId(articleId);
        if (tagNames.isEmpty()) {
            return;
        }
        List<Long> tagIds = new ArrayList<>();
        for (String name : tagNames) {
            tagIds.add(resolveSingleTag(name));
        }
        baseMapper.insertArticleTags(articleId, tagIds);
    }

    /** 按名查找，不存在则创建；并发下唯一键冲突时回填已存在的那条 */
    private Long resolveSingleTag(String name) {
        Tag existing = tagMapper.selectOne(new LambdaQueryWrapper<Tag>().eq(Tag::getName, name));
        if (existing != null) {
            return existing.getId();
        }
        try {
            Tag tag = new Tag();
            tag.setName(name);
            tagMapper.insert(tag);
            return tag.getId();
        } catch (Exception e) {
            Tag again = tagMapper.selectOne(new LambdaQueryWrapper<Tag>().eq(Tag::getName, name));
            if (again != null) {
                return again.getId();
            }
            throw e;
        }
    }
}
