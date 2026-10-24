package com.example.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.blog.entity.Comment;
import com.example.blog.vo.CommentVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CommentMapper extends BaseMapper<Comment> {

    /** 一篇文章下的全部正常评论（含顶级与回复），由 Service 组装成两层树 */
    List<CommentVO> selectByArticleId(@Param("articleId") Long articleId);

    int countByArticleId(@Param("articleId") Long articleId);
}
