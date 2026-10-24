package com.example.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.blog.dto.ArticleQuery;
import com.example.blog.entity.Article;
import com.example.blog.vo.ArchiveItemVO;
import com.example.blog.vo.ArticleDetailVO;
import com.example.blog.vo.ArticleVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ArticleMapper extends BaseMapper<Article> {

    /** 列表页/搜索结果：返回轻量 VO，带分类名、作者名、标签串 */
    List<ArticleVO> selectArticleList(IPage<ArticleVO> page, @Param("q") ArticleQuery query);

    /** 详情页：补全正文 HTML */
    ArticleDetailVO selectDetailById(@Param("id") Long id);

    /** 归档：拉取全部已发布文章的标题与时间，由 Service 按年月分组 */
    List<ArchiveItemVO> selectArchiveItems();

    List<Long> selectTagIdsByArticleId(@Param("articleId") Long articleId);

    int deleteTagsByArticleId(@Param("articleId") Long articleId);

    int insertArticleTags(@Param("articleId") Long articleId, @Param("tagIds") List<Long> tagIds);

    /** 浏览量自增，D7 已改为 Redis 增量 + 定时回刷 */
    int increaseViewCount(@Param("id") Long id);

    /** 回刷：把 Redis 累积的浏览增量批量加到 DB 基数上 */
    int addViewCount(@Param("id") Long id, @Param("delta") long delta);

    /** 点赞数自增/自减（取消点赞时防负数） */
    int increaseLikeCount(@Param("id") Long id);

    int decreaseLikeCount(@Param("id") Long id);

    int increaseCommentCount(@Param("id") Long id);

    int decreaseCommentCount(@Param("id") Long id);

    /** 删除分类前把文章的分类置空，避免外键孤儿 */
    int clearCategory(@Param("categoryId") Long categoryId);

    long countByCategoryId(@Param("categoryId") Long categoryId);

    /** 删除标签前清理关联关系 */
    int deleteTagsByTagId(@Param("tagId") Long tagId);
}
