package com.example.blog;

import com.example.blog.common.BusinessException;
import com.example.blog.dto.ArticleSaveRequest;
import com.example.blog.dto.CommentRequest;
import com.example.blog.entity.Article;
import com.example.blog.mapper.ArticleMapper;
import com.example.blog.service.ArticleService;
import com.example.blog.service.CommentService;
import com.example.blog.vo.CommentVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 评论链路自检：两层树组装、非法回复拦截、计数增减。
 * 标注 @Transactional，测试结束整体回滚，不污染真实数据。
 */
@SpringBootTest
@Transactional
class CommentServiceTest {

    @Autowired
    private CommentService commentService;

    @Autowired
    private ArticleService articleService;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private com.example.blog.util.CacheService cacheService;

    private Long articleId;

    @BeforeEach
    void setUp() {
        cacheService.deleteByPrefix("blog:");
        ArticleSaveRequest request = new ArticleSaveRequest();
        request.setTitle("评论测试用文章");
        request.setContent("正文内容，用于承载评论。");
        request.setStatus(1);
        request.setIsTop(0);
        request.setIsOriginal(1);
        articleId = articleService.saveArticle(request, 1L);
    }

    @Test
    void shouldBuildTwoLevelTree() {
        Long topId = commentService.addComment(req(null, null, "第一条顶级评论"), 1L, "127.0.0.1");
        Long replyId = commentService.addComment(req(topId, topId, "回复第一条"), 2L, "127.0.0.1");
        Long secondTopId = commentService.addComment(req(null, null, "第二条顶级评论"), 2L, "127.0.0.1");

        List<CommentVO> tree = commentService.listByArticle(articleId);
        assertEquals(2, tree.size(), "应只有两条顶级评论");
        assertEquals(1, tree.get(0).getReplies().size(), "第一条顶级评论下应挂一条回复");
        assertEquals(replyId, tree.get(0).getReplies().get(0).getId());
        assertEquals(secondTopId, tree.get(1).getId());

        CommentVO reply = tree.get(0).getReplies().get(0);
        assertNotNull(reply.getReplyToName(), "回复应能取到被回复者昵称");
        assertTrue(reply.getReplies().isEmpty(), "回复下不应再有子节点（只支持两层）");

        Article article = articleMapper.selectById(articleId);
        assertEquals(3, article.getCommentCount(), "评论数应累加到 3");
    }

    @Test
    void shouldRejectNestedReplyBeyondTwoLevels() {
        Long topId = commentService.addComment(req(null, null, "顶级"), 1L, "127.0.0.1");
        Long replyId = commentService.addComment(req(topId, topId, "回复"), 2L, "127.0.0.1");

        // 拿子回复当父级，应被拒绝（只支持两层）
        assertThrows(BusinessException.class,
                () -> commentService.addComment(req(replyId, replyId, "再回复一层"), 1L, "127.0.0.1"));
    }

    @Test
    void shouldRejectParentFromOtherArticle() {
        ArticleSaveRequest other = new ArticleSaveRequest();
        other.setTitle("另一篇文章");
        other.setContent("另一篇正文");
        other.setStatus(1);
        Long otherId = articleService.saveArticle(other, 1L);

        Long otherTopId = commentService.addComment(reqOn(otherId, null, null, "别处的评论"), 1L, "127.0.0.1");
        // 用别处评论的 ID 当 parent 发到当前文章，应被拒绝
        assertThrows(BusinessException.class,
                () -> commentService.addComment(req(otherTopId, otherTopId, "跨文章回复"), 1L, "127.0.0.1"));
    }

    @Test
    void shouldRejectCommentOnUnpublishedArticle() {
        ArticleSaveRequest draft = new ArticleSaveRequest();
        draft.setTitle("草稿文章");
        draft.setContent("草稿正文");
        draft.setStatus(0);
        Long draftId = articleService.saveArticle(draft, 1L);

        assertThrows(BusinessException.class,
                () -> commentService.addComment(reqOn(draftId, null, null, "评论草稿"), 1L, "127.0.0.1"));
    }

    @Test
    void shouldDecreaseCountWhenDelete() {
        Long topId = commentService.addComment(req(null, null, "待删除评论"), 1L, "127.0.0.1");
        assertEquals(1, articleMapper.selectById(articleId).getCommentCount());

        commentService.deleteComment(topId, 1L, false);
        assertEquals(0, articleMapper.selectById(articleId).getCommentCount(), "删除后计数应回退");
        assertTrue(commentService.listByArticle(articleId).isEmpty());
    }

    @Test
    void shouldForbidDeletingOthersComment() {
        Long topId = commentService.addComment(req(null, null, "别人的评论"), 1L, "127.0.0.1");
        assertThrows(BusinessException.class, () -> commentService.deleteComment(topId, 999L, false));
        // 管理员不受限
        commentService.deleteComment(topId, 999L, true);
    }

    private CommentRequest req(Long parentId, Long replyToId, String content) {
        return reqOn(articleId, parentId, replyToId, content);
    }

    private CommentRequest reqOn(Long targetArticleId, Long parentId, Long replyToId, String content) {
        CommentRequest request = new CommentRequest();
        request.setArticleId(targetArticleId);
        request.setParentId(parentId);
        request.setReplyToId(replyToId);
        request.setContent(content);
        return request;
    }
}
