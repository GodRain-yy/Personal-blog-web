package com.example.blog.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.example.blog.dto.ArticleQuery;
import com.example.blog.dto.ArticleSaveRequest;
import com.example.blog.common.PageResult;
import com.example.blog.entity.Article;
import com.example.blog.vo.ArchiveGroupVO;
import com.example.blog.vo.ArticleDetailVO;
import com.example.blog.vo.ArticleVO;
import com.example.blog.vo.HotItemVO;

import java.util.List;

public interface ArticleService extends IService<Article> {

    /** 前台列表 / 后台列表共用，靠 query.status 区分 */
    PageResult<ArticleVO> listArticles(ArticleQuery query);

    /** 文章详情，未发布或不存在返回 null；同时累加浏览量 */
    ArticleDetailVO getDetail(Long id);

    /** 归档：按年月倒序分组 */
    List<ArchiveGroupVO> getArchive();

    /** 热门榜：按 Redis ZSet 热度倒序取前 N 篇已发布文章 */
    List<HotItemVO> listHot(int limit);

    /** 保存或更新文章，自动渲染 Markdown、补全摘要与字数、维护标签关联 */
    Long saveArticle(ArticleSaveRequest request, Long userId);

    /** 删除文章及其标签关联 */
    void deleteArticle(Long id);

    /** 后台编辑回显用 */
    ArticleSaveRequest getEditForm(Long id);
}
