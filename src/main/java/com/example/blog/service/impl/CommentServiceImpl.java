package com.example.blog.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.example.blog.common.BusinessException;
import com.example.blog.common.ErrorCode;
import com.example.blog.dto.CommentRequest;
import com.example.blog.entity.Article;
import com.example.blog.entity.Comment;
import com.example.blog.mapper.ArticleMapper;
import com.example.blog.mapper.CommentMapper;
import com.example.blog.service.CommentService;
import com.example.blog.util.CacheKeys;
import com.example.blog.util.CacheService;
import com.example.blog.vo.CommentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService {

    private final ArticleMapper articleMapper;

    private final CacheService cacheService;

    @Override
    public List<CommentVO> listByArticle(Long articleId) {
        if (articleId == null) {
            return List.of();
        }
        List<CommentVO> all = baseMapper.selectByArticleId(articleId);
        if (all == null || all.isEmpty()) {
            return List.of();
        }

        List<CommentVO> tops = new ArrayList<>();
        Map<Long, CommentVO> topIndex = new HashMap<>();
        for (CommentVO c : all) {
            if (isTopLevel(c)) {
                tops.add(c);
                topIndex.put(c.getId(), c);
            }
        }
        // SQL 已按时间升序，直接按序挂载即保持时间顺序
        for (CommentVO c : all) {
            if (!isTopLevel(c)) {
                CommentVO parent = topIndex.get(c.getParentId());
                if (parent != null) {
                    parent.getReplies().add(c);
                }
            }
        }
        return tops;
    }

    @Override
    @Transactional
    public Long addComment(CommentRequest request, Long userId, String ip) {
        Article article = articleMapper.selectById(request.getArticleId());
        if (article == null || !Integer.valueOf(1).equals(article.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文章不存在或未发布");
        }

        Long parentId = (request.getParentId() == null) ? 0L : request.getParentId();
        Long replyToId = request.getReplyToId();

        // 只允许两层：回复必须挂在顶级评论下，且必须属于同一篇文章
        if (parentId != 0) {
            Comment parent = getById(parentId);
            if (parent == null || !parent.getArticleId().equals(article.getId()) || parent.getParentId() != 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "回复的评论不存在");
            }
        }
        if (replyToId != null && replyToId != 0) {
            Comment target = getById(replyToId);
            if (target == null || !target.getArticleId().equals(article.getId())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "被回复的评论不存在");
            }
        }

        Comment comment = new Comment();
        comment.setArticleId(article.getId());
        comment.setUserId(userId);
        comment.setParentId(parentId);
        comment.setReplyToId((replyToId == null || replyToId == 0) ? null : replyToId);
        comment.setContent(request.getContent().trim());
        comment.setStatus(1);
        comment.setIp(ip);
        save(comment);

        articleMapper.increaseCommentCount(article.getId());
        // 列表页展示评论数，必须让列表缓存失效，否则数字一直不更新
        cacheService.deleteByPrefixAfterCommit(CacheKeys.ARTICLE_LIST);
        // 评论权重高，热门榜 +10
        cacheService.zincrHot(article.getId(), 10);
        return comment.getId();
    }

    @Override
    @Transactional
    public void deleteComment(Long id, Long userId, boolean isAdmin) {
        Comment comment = getById(id);
        if (comment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "评论不存在");
        }
        if (!isAdmin && !comment.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能删除自己的评论");
        }
        removeById(id);
        articleMapper.decreaseCommentCount(comment.getArticleId());
        cacheService.deleteByPrefixAfterCommit(CacheKeys.ARTICLE_LIST);
    }

    private boolean isTopLevel(CommentVO c) {
        return c.getParentId() == null || c.getParentId() == 0;
    }
}
