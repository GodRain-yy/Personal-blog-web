package com.example.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评论。只支持两层结构：
 * parent_id = 0 表示顶级评论；回复时 parent_id 填所属顶级评论 ID，reply_to_id 填被回复的评论 ID。
 */
@Data
@TableName("comment")
public class Comment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long articleId;

    private Long userId;

    private Long parentId;

    private Long replyToId;

    private String content;

    /** 1正常 0待审核 2已删除 */
    private Integer status;

    private String ip;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
