package com.example.blog.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体。表名 blog_user —— 前缀避开 MySQL 保留字 user。
 */
@Data
@TableName("blog_user")
public class User {

    private Long id;

    private String username;

    private String password;

    private String nickname;

    private String email;

    private String avatar;

    private String intro;

    /** 0 普通用户，1 管理员 */
    private Integer role;

    /** 1 正常，0 禁用 */
    private Integer status;

    private LocalDateTime lastLoginAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
