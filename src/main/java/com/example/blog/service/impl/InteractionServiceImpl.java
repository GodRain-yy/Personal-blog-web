package com.example.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.blog.common.BusinessException;
import com.example.blog.common.ErrorCode;
import com.example.blog.entity.Article;
import com.example.blog.entity.ArticleFavorite;
import com.example.blog.entity.UserLike;
import com.example.blog.mapper.ArticleFavoriteMapper;
import com.example.blog.mapper.ArticleMapper;
import com.example.blog.mapper.UserLikeMapper;
import com.example.blog.service.InteractionService;
import com.example.blog.util.CacheKeys;
import com.example.blog.util.CacheService;
import com.example.blog.vo.InteractionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class InteractionServiceImpl implements InteractionService {

    /** 点赞对热度贡献 */
    private static final double LIKE_WEIGHT = 5.0;

    /** 收藏对热度贡献 */
    private static final double FAVORITE_WEIGHT = 3.0;

    private final UserLikeMapper userLikeMapper;

    private final ArticleFavoriteMapper favoriteMapper;

    private final ArticleMapper articleMapper;

    private final CacheService cacheService;

    @Override
    @Transactional
    public InteractionVO toggleLike(Long userId, Long articleId) {
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文章不存在");
        }

        UserLike existing = userLikeMapper.selectOne(new LambdaQueryWrapper<UserLike>()
                .eq(UserLike::getUserId, userId)
                .eq(UserLike::getArticleId, articleId));

        InteractionVO vo = new InteractionVO();
        if (existing == null) {
            UserLike like = new UserLike();
            like.setUserId(userId);
            like.setArticleId(articleId);
            like.setCreateTime(LocalDateTime.now());
            userLikeMapper.insert(like);
            articleMapper.increaseLikeCount(articleId);
            cacheService.zincrHot(articleId, LIKE_WEIGHT);
            vo.setLiked(true);
        } else {
            userLikeMapper.deleteById(existing.getId());
            articleMapper.decreaseLikeCount(articleId);
            cacheService.zincrHot(articleId, -LIKE_WEIGHT);
            vo.setLiked(false);
        }
        // 列表缓存里带着 like_count，失效以保证数字刷新
        cacheService.deleteByPrefixAfterCommit(CacheKeys.ARTICLE_LIST);
        vo.setLikeCount(articleMapper.selectById(articleId).getLikeCount());
        vo.setFavorited(isFavorited(userId, articleId));
        return vo;
    }

    @Override
    @Transactional
    public InteractionVO toggleFavorite(Long userId, Long articleId) {
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文章不存在");
        }

        ArticleFavorite existing = favoriteMapper.selectOne(new LambdaQueryWrapper<ArticleFavorite>()
                .eq(ArticleFavorite::getUserId, userId)
                .eq(ArticleFavorite::getArticleId, articleId));

        InteractionVO vo = new InteractionVO();
        if (existing == null) {
            ArticleFavorite fav = new ArticleFavorite();
            fav.setUserId(userId);
            fav.setArticleId(articleId);
            fav.setCreateTime(LocalDateTime.now());
            favoriteMapper.insert(fav);
            cacheService.zincrHot(articleId, FAVORITE_WEIGHT);
            vo.setFavorited(true);
        } else {
            favoriteMapper.deleteById(existing.getId());
            cacheService.zincrHot(articleId, -FAVORITE_WEIGHT);
            vo.setFavorited(false);
        }
        vo.setLiked(isLiked(userId, articleId));
        vo.setLikeCount(article.getLikeCount());
        return vo;
    }

    @Override
    public InteractionVO getStatus(Long userId, Long articleId) {
        InteractionVO vo = new InteractionVO();
        if (userId == null) {
            return vo;
        }
        vo.setLiked(isLiked(userId, articleId));
        vo.setFavorited(isFavorited(userId, articleId));
        Article article = articleMapper.selectById(articleId);
        vo.setLikeCount(article == null ? 0 : article.getLikeCount());
        return vo;
    }

    private boolean isLiked(Long userId, Long articleId) {
        return userLikeMapper.selectCount(new LambdaQueryWrapper<UserLike>()
                .eq(UserLike::getUserId, userId)
                .eq(UserLike::getArticleId, articleId)) > 0;
    }

    private boolean isFavorited(Long userId, Long articleId) {
        return favoriteMapper.selectCount(new LambdaQueryWrapper<ArticleFavorite>()
                .eq(ArticleFavorite::getUserId, userId)
                .eq(ArticleFavorite::getArticleId, articleId)) > 0;
    }
}
