# 个人博客系统（bokersystem_web）

基于 **Spring Boot 4 + Thymeleaf** 的单体博客，服务端渲染、Session 表单登录。业务逻辑全部沉淀在 Service 层，未来若要改前后端分离，改动止于 Controller，Service/Mapper/DB/Redis 零改动。

## 技术栈

| 层 | 选型 |
| --- | --- |
| 语言 / 构建 | Java 25、Maven 3.9 |
| Web 框架 | Spring Boot 4.1、Spring MVC、Thymeleaf 3 |
| 安全 | Spring Security 表单登录 + Session、Remember-Me 7 天、CSRF 开启 |
| ORM | MyBatis-Plus 3.5.17 |
| 数据库 | MySQL 8 / 9（中文全文检索用 ngram 解析器） |
| 缓存 | Redis（详情/列表/侧边栏缓存 + 浏览量增量 + 热门榜 ZSet） |
| 前端 | Bootstrap 5.3、Vditor 3.10（Markdown 编辑器，CDN） |
| 文档渲染 | commonmark + OWASP Java HTML Sanitizer（白名单防 XSS） |

## 功能

- 用户：注册、登录、退出、Remember-Me、首次启动自动建管理员
- 文章：Markdown 撰写与渲染、摘要自动截取、字数统计、草稿/发布/回收站、置顶、原创标记
- 分类 / 标签：后台增删改，删除前自动清理孤儿关联
- 搜索：标题+摘要 ngram 全文索引（单字符降级 LIKE）
- 评论：两级评论、回复、登录后可发表、管理员/作者可删除
- 互动：点赞、收藏（幂等切换），详情页 Ajax 实时高亮
- 热门榜：按「浏览×1 + 点赞×5 + 评论×10 + 收藏×3」热度倒序
- 性能：Redis 缓存详情/列表/侧边栏；浏览量改为 Redis 增量累积、每 60 秒批量回刷 DB
- 安全：存储型 XSS 白名单过滤、CSRF 全站开启、文件上传类型白名单 + 5MB 限制 + UUID 重命名防目录穿越、评论内容转义输出

## 环境要求

- JDK 25
- MySQL 8.0+（**必须**设置 `ngram_token_size=2` 并重启，否则中文全文检索无效）
- Redis 运行在 6379

## 运行

```bash
# 1. 建库（已内置 sql/schema.sql，也可让应用首次启动时自动建表）
mysql -uroot -p < sql/schema.sql

# 2. 配置数据源 / Redis（见 application-dev.yml，密码建议用环境变量覆盖）
export DB_PASSWORD=yourpassword
export REDIS_HOST=localhost

# 3. 打包运行
mvn -q package -DskipTests
java -jar target/bokersystem-web-0.0.1-SNAPSHOT.jar --server.port=8080
```

启动后访问 http://localhost:8080 。后台 /admin ，首次启动自动创建管理员：

- 账号：`admin`
- 密码：`admin123`（通过 `blog.init-admin.password` 环境变量可覆盖）

> 配置里的 MySQL 密码为本地默认值，生产环境请一律用 `DB_PASSWORD` 等环境变量覆盖，不要明文提交。

## 测试

```bash
mvn test
```

测试覆盖：Markdown 渲染与 XSS 过滤、分页、缓存命中/失效/空值占位、分类标签删除、ngram 全文检索（EXPLAIN 确认命中索引）、两层评论树、点赞收藏幂等、热门榜更新、浏览量回刷写库、端到端登录发评论。

## 接口一览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/` `/index` `/search` `/hot` `/archives` | 前台页面 |
| GET | `/articles/{id}` | 文章详情（顺带累加浏览量） |
| GET/POST | `/login` `/register` `/logout` | 认证 |
| POST | `/comments` `/comments/{id}/delete` | 评论（PRG 重定向） |
| POST | `/api/like/toggle` `/api/favorite/toggle` | 点赞/收藏（JSON，需登录） |
| GET | `/api/interaction/{id}` | 查询当前用户点赞/收藏状态 |
| POST | `/api/admin/upload/image` | 图片上传（Vditor，JSON） |
| 后台 | `/admin/**`（需 ADMIN 角色） | 文章/分类/标签管理 |
