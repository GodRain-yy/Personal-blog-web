-- 个人博客系统 建库建表脚本
-- MySQL 9.7 / 8.0 均适用
-- 注意：全文索引使用 ngram 解析器，需先确认 MySQL 配置 ngram_token_size=2 并重启，否则中文检索无效

CREATE DATABASE IF NOT EXISTS blog
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE blog;

-- 1. 用户表
CREATE TABLE IF NOT EXISTS blog_user (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL COMMENT '用户名',
    password      VARCHAR(100) NOT NULL COMMENT 'BCrypt 加密',
    nickname      VARCHAR(50)  COMMENT '昵称',
    email         VARCHAR(100) COMMENT '邮箱',
    avatar        VARCHAR(255) COMMENT '头像 URL',
    intro         VARCHAR(255) COMMENT '个人简介',
    role          TINYINT      NOT NULL DEFAULT 0 COMMENT '0普通用户 1管理员',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 0禁用',
    last_login_at DATETIME     COMMENT '最后登录时间',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_email (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';

-- 2. 分类表
CREATE TABLE IF NOT EXISTS category (
    id          BIGINT      AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(50) NOT NULL COMMENT '分类名',
    sort        INT         NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '分类表';

-- 3. 标签表
CREATE TABLE IF NOT EXISTS tag (
    id          BIGINT      AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(50) NOT NULL COMMENT '标签名',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '标签表';

-- 4. 文章表
CREATE TABLE IF NOT EXISTS article (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT       NOT NULL COMMENT '作者 ID',
    category_id   BIGINT       COMMENT '分类 ID',
    title         VARCHAR(200) NOT NULL COMMENT '标题',
    summary       VARCHAR(500) COMMENT '摘要',
    content       LONGTEXT     COMMENT 'Markdown 原文',
    content_html  LONGTEXT     COMMENT '渲染后且已过白名单的 HTML',
    cover         VARCHAR(255) COMMENT '封面图 URL',
    status        TINYINT      NOT NULL DEFAULT 0 COMMENT '0草稿 1发布 2回收站',
    is_top        TINYINT      NOT NULL DEFAULT 0 COMMENT '是否置顶 0否 1是',
    is_original   TINYINT      NOT NULL DEFAULT 1 COMMENT '是否原创 0否 1是',
    view_count    INT          NOT NULL DEFAULT 0 COMMENT '浏览量（冗余）',
    like_count    INT          NOT NULL DEFAULT 0 COMMENT '点赞数（冗余）',
    comment_count INT          NOT NULL DEFAULT 0 COMMENT '评论数（冗余）',
    word_count    INT          NOT NULL DEFAULT 0 COMMENT '正文字数',
    publish_time  DATETIME     COMMENT '发布时间',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_user (user_id),
    KEY idx_category (category_id),
    KEY idx_create_time (create_time),
    KEY idx_status_publish (status, publish_time),
    FULLTEXT KEY ft_title_summary (title, summary) WITH PARSER ngram
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '文章表';

-- 5. 文章-标签关联表
CREATE TABLE IF NOT EXISTS article_tag (
    article_id BIGINT NOT NULL,
    tag_id     BIGINT NOT NULL,
    PRIMARY KEY (article_id, tag_id),
    KEY idx_tag (tag_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '文章标签关联表';

-- 6. 评论表（只支持两层：顶级评论 parent_id=0，回复指向顶级评论）
CREATE TABLE IF NOT EXISTS comment (
    id           BIGINT      AUTO_INCREMENT PRIMARY KEY,
    article_id   BIGINT      NOT NULL COMMENT '文章 ID',
    user_id      BIGINT      NOT NULL COMMENT '评论者 ID',
    parent_id    BIGINT      NOT NULL DEFAULT 0 COMMENT '0=顶级评论，非0=所属顶级评论 ID',
    reply_to_id  BIGINT      COMMENT '回复的目标评论 ID（同层展示用）',
    content      TEXT        NOT NULL COMMENT '评论内容',
    status       TINYINT     NOT NULL DEFAULT 1 COMMENT '1正常 0待审核 2已删除',
    ip           VARCHAR(45) COMMENT 'IP，IPv6 最长 45 字符',
    create_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_article (article_id),
    KEY idx_parent (parent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '评论表';

-- 7. 点赞表
CREATE TABLE IF NOT EXISTS user_like (
    id          BIGINT   AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT   NOT NULL,
    article_id  BIGINT   NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_article (user_id, article_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '点赞表';

-- 8. 收藏表
CREATE TABLE IF NOT EXISTS article_favorite (
    id          BIGINT   AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT   NOT NULL,
    article_id  BIGINT   NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_article (user_id, article_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '收藏表';
