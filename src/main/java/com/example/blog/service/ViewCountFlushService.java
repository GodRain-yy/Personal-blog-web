package com.example.blog.service;

import com.example.blog.mapper.ArticleMapper;
import com.example.blog.util.CacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 浏览量增量回刷。
 *
 * 设计：getDetail 每次访问只给 Redis 的 viewincr 计数器 +1，不写 DB，
 * 避免高频访问把 view_count 的 UPDATE 打爆数据库。
 * 这里每 60 秒把累积增量刷回 article.view_count，再删掉增量 key。
 * 即使进程崩溃，最多丢 60 秒内的浏览增量（对博客可接受，且不会回退）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ViewCountFlushService {

    private final ArticleMapper articleMapper;

    private final CacheService cacheService;

    @Scheduled(fixedDelay = 60_000)
    public void flush() {
        Map<Long, Long> incrs = cacheService.collectViewIncrs();
        if (incrs.isEmpty()) {
            return;
        }
        int flushed = 0;
        for (Map.Entry<Long, Long> entry : incrs.entrySet()) {
            Long articleId = entry.getKey();
            long delta = entry.getValue();
            try {
                if (delta > 0) {
                    articleMapper.addViewCount(articleId, delta);
                }
            } catch (Exception e) {
                // 文章可能被删了，清理掉残留 key 即可，不必中断整轮
                log.warn("浏览量回刷失败，跳过. articleId={}, msg={}", articleId, e.getMessage());
            } finally {
                cacheService.deleteViewIncr(articleId);
                flushed++;
            }
        }
        log.debug("浏览量回刷完成，处理 {} 个文章", flushed);
    }
}
