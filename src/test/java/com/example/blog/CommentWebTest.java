package com.example.blog;

import com.example.blog.dto.ArticleSaveRequest;
import com.example.blog.service.ArticleService;
import com.example.blog.service.CommentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 评论的端到端链路：表单登录（带 CSRF）→ 发表评论 → 详情页回显。
 * 验证 Thymeleaf 表单 + Spring Security + @AuthenticationPrincipal 三者串得通。
 * 用 JDK HttpClient + CookieManager 管理会话，不依赖 TestRestTemplate（Spring Boot 4 已移除）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CommentWebTest {

    private static final String ADMIN = "admin";
    private static final String PASSWORD = "admin123";

    @LocalServerPort
    private int port;

    @Autowired
    private ArticleService articleService;

    @Autowired
    private CommentService commentService;

    private HttpClient client;

    private Long articleId;

    @BeforeEach
    void setUp() {
        CookieManager cookies = new CookieManager();
        client = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        ArticleSaveRequest request = new ArticleSaveRequest();
        request.setTitle("Web 端到端评论测试文章");
        request.setContent("正文");
        request.setStatus(1);
        request.setIsTop(0);
        request.setIsOriginal(1);
        articleId = articleService.saveArticle(request, 1L);
    }

    @AfterEach
    void cleanUp() {
        if (articleId != null) {
            commentService.listByArticle(articleId).forEach(c -> {
                c.getReplies().forEach(r -> commentService.deleteComment(r.getId(), 1L, true));
                commentService.deleteComment(c.getId(), 1L, true);
            });
            articleService.deleteArticle(articleId);
        }
    }

    @Test
    void shouldCommentAfterLogin() throws Exception {
        String base = "http://localhost:" + port;

        // 1. 未登录：详情页应提示去登录，且不能报 500
        HttpResponse<String> anonymous = get(base + "/articles/" + articleId);
        assertEquals(200, anonymous.statusCode(), "未登录也应能看文章详情");
        assertTrue(anonymous.body().contains("后可以发表评论"), "未登录应提示先登录");

        // 2. 取登录页 CSRF 并登录
        String loginPage = get(base + "/login").body();
        String csrf = extractCsrf(loginPage);
        assertNotNull(csrf, "登录页应包含 CSRF token");

        HttpResponse<String> loginResp = post(base + "/login",
                "username=" + enc(ADMIN) + "&password=" + enc(PASSWORD) + "&_csrf=" + enc(csrf));
        assertEquals(302, loginResp.statusCode(), "登录成功应 302，实际 " + loginResp.statusCode());
        String location = loginResp.headers().firstValue("Location").orElse("");
        assertTrue(location.endsWith("/"), "登录成功后应跳首页，实际: " + location);

        // 3. 登录态下详情页应出现评论输入框
        String detail = get(base + "/articles/" + articleId).body();
        assertTrue(detail.contains("说点什么吧"), "登录后应看到评论输入框");
        String commentCsrf = extractCsrf(detail);
        assertNotNull(commentCsrf, "详情页表单应带 CSRF token");

        // 4. 发表评论
        HttpResponse<String> postResp = post(base + "/comments",
                "articleId=" + articleId + "&parentId=0&content=" + enc("端到端测试评论内容")
                        + "&_csrf=" + enc(commentCsrf));
        assertEquals(302, postResp.statusCode(), "发表评论后应重定向（PRG），实际 " + postResp.statusCode());
        assertTrue(postResp.headers().firstValue("Location").orElse("").contains("/articles/" + articleId),
                "应重定向回文章详情");

        // 5. 评论应回显在详情页
        String after = get(base + "/articles/" + articleId).body();
        assertTrue(after.contains("端到端测试评论内容"), "评论应出现在详情页");

        // 6. 未带 CSRF 的 POST 应被拒绝
        HttpResponse<String> noCsrf = post(base + "/comments",
                "articleId=" + articleId + "&parentId=0&content=" + enc("无 token 提交"));
        assertTrue(noCsrf.statusCode() == 403 || noCsrf.statusCode() == 401,
                "缺少 CSRF token 应被拒绝，实际 " + noCsrf.statusCode());
    }

    private HttpResponse<String> get(String url) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "text/html")
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> post(String url, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "text/html")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String extractCsrf(String html) {
        Matcher m = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"").matcher(html);
        if (m.find()) {
            return m.group(1);
        }
        m = Pattern.compile("value=\"([^\"]+)\"\\s+name=\"_csrf\"").matcher(html);
        return m.find() ? m.group(1) : null;
    }
}
