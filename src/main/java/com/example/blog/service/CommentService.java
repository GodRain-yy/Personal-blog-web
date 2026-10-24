package com.example.blog.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.example.blog.dto.CommentRequest;
import com.example.blog.entity.Comment;
import com.example.blog.vo.CommentVO;

import java.util.List;

public interface CommentService extends IService<Comment> {

    /** 按文章查询评论树，只展开两层 */
    List<CommentVO> listByArticle(Long articleId);

    /**
     * 发表评论。
     *
     * @param ip 由 Controller 从请求中取出后传入，Service 层不接触 Web 类型
     */
    Long addComment(CommentRequest request, Long userId, String ip);

    /** 作者本人或管理员删除，同时回退文章评论数 */
    void deleteComment(Long id, Long userId, boolean isAdmin);
}
