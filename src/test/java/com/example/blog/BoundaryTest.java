package com.example.blog;

import com.example.blog.common.BusinessException;
import com.example.blog.common.ErrorCode;
import com.example.blog.dto.ArticleSaveRequest;
import com.example.blog.service.ArticleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class BoundaryTest {

    @Autowired
    private ArticleService articleService;

    @Test
    void emptyTitle_should_be_rejected() {
        ArticleSaveRequest req = new ArticleSaveRequest();
        req.setTitle("   ");
        req.setContent("正文");
        req.setStatus(1);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> articleService.saveArticle(req, 1L));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    void overlongTitle_should_be_rejected() {
        ArticleSaveRequest req = new ArticleSaveRequest();
        req.setTitle("标".repeat(201));
        req.setContent("正文");
        req.setStatus(1);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> articleService.saveArticle(req, 1L));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    void overlongSummary_should_be_rejected() {
        ArticleSaveRequest req = new ArticleSaveRequest();
        req.setTitle("正常标题");
        req.setSummary("摘".repeat(501));
        req.setContent("正文");
        req.setStatus(1);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> articleService.saveArticle(req, 1L));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }
}
