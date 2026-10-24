package com.example.blog.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 缓存读写封装。三个硬性要求：
 * 1. Redis 挂了只能降级，绝不能把异常抛到业务层导致整站 500
 * 2. 过期时间带随机抖动，避免同一批 key 同时失效引发雪崩
 * 3. 写操作在事务提交后再删缓存（否则可能读到旧值又被回填）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheService {

    private static final Random RANDOM = new Random();

    private final RedisTemplate<String, Object> redisTemplate;

    /** 计数器/热门榜用 StringRedisTemplate：值是纯字符串数字，保证 INCR / ZINCRBY 不会被 JSON 序列化干扰 */
    private final StringRedisTemplate stringRedisTemplate;

    public Optional<Object> get(String key) {
        try {
            return Optional.ofNullable(redisTemplate.opsForValue().get(key));
        } catch (Exception e) {
            log.warn("缓存读取失败，降级走数据库. key={}, msg={}", key, e.getMessage());
            return Optional.empty();
        }
    }

    /** 存普通值，TTL 在 base 上加 0~jitter 秒的随机抖动 */
    public void set(String key, Object value, Duration baseTtl, long jitterSeconds) {
        try {
            redisTemplate.opsForValue().set(key, value, baseTtl.plusSeconds(randomJitter(jitterSeconds)));
        } catch (Exception e) {
            log.warn("缓存写入失败，忽略. key={}, msg={}", key, e.getMessage());
        }
    }

    /**
     * 存空值占位。缓存穿透保护：查询 DB 为空时也要写一条短命占位，
     * 否则恶意刷不存在的 id 会每次都打到数据库。
     */
    public void setNull(String key, Duration ttl) {
        set(key, CacheKeys.NULL_PLACEHOLDER, ttl, 0);
    }

    public boolean isNullPlaceholder(Object value) {
        return CacheKeys.NULL_PLACEHOLDER.equals(value);
    }

    public void delete(String... keys) {
        try {
            redisTemplate.delete(List.of(keys));
        } catch (Exception e) {
            log.warn("缓存删除失败，忽略. keys={}, msg={}", List.of(keys), e.getMessage());
        }
    }

    /**
     * 按前缀批量删除。列表缓存 key 带 MD5，无法逐个枚举，只能前缀扫描。
     * 用 SCAN 不用 KEYS —— KEYS 会阻塞 Redis 单线程，数据量大时是事故。
     */
    public void deleteByPrefix(String prefix) {
        try {
            ScanOptions options = ScanOptions.scanOptions().match(prefix + "*").count(200).build();
            Set<String> toDelete = new HashSet<>();
            try (var cursor = redisTemplate.scan(options)) {
                while (cursor.hasNext()) {
                    toDelete.add(cursor.next());
                }
            }
            if (!toDelete.isEmpty()) {
                redisTemplate.delete(toDelete);
                log.debug("按前缀清理缓存 {} 命中 {} 个 key", prefix, toDelete.size());
            }
        } catch (Exception e) {
            log.warn("前缀清理缓存失败，忽略. prefix={}, msg={}", prefix, e.getMessage());
        }
    }

    /**
     * 事务提交后再删缓存。
     * 若在事务内删，可能删完又被另一个请求用事务提交前的旧值回填，缓存就脏了。
     */
    public void deleteAfterCommit(String... keys) {
        Runnable task = () -> delete(keys);
        registerAfterCommit(task);
    }

    public void deleteByPrefixAfterCommit(String prefix) {
        registerAfterCommit(() -> deleteByPrefix(prefix));
    }

    private void registerAfterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            // 没有事务就立即执行，避免缓存漏删
            task.run();
        }
    }

    private long randomJitter(long seconds) {
        return seconds <= 0 ? 0 : RANDOM.nextLong(seconds + 1);
    }

    /** 测试用：判断 key 是否还在 */
    public boolean exists(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            return false;
        }
    }

    /** 测试用：剩余过期时间 */
    public long ttlSeconds(String key) {
        try {
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            return ttl == null ? -1 : ttl;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 浏览量增量 +1，返回累计增量值。
     * 用 StringRedisTemplate，值存为纯数字字符串，INCR 才可靠。
     */
    public long incrementView(Long articleId) {
        try {
            Long v = stringRedisTemplate.opsForValue().increment(CacheKeys.articleViewIncr(articleId));
            return v == null ? 0 : v;
        } catch (Exception e) {
            log.warn("浏览量增量失败，忽略. articleId={}, msg={}", articleId, e.getMessage());
            return 0;
        }
    }

    /** 读取某文章累计的浏览增量（未回刷到 DB 的部分） */
    public long getViewIncr(Long articleId) {
        try {
            String v = stringRedisTemplate.opsForValue().get(CacheKeys.articleViewIncr(articleId));
            return v == null ? 0 : Long.parseLong(v);
        } catch (Exception e) {
            return 0;
        }
    }

    /** 回刷后删除浏览增量 key */
    public void deleteViewIncr(Long articleId) {
        try {
            stringRedisTemplate.delete(CacheKeys.articleViewIncr(articleId));
        } catch (Exception e) {
            log.warn("删除浏览增量 key 失败，忽略. articleId={}", articleId);
        }
    }

    /**
     * 收集全部浏览增量 key（用 SCAN，不阻塞 Redis 单线程），返回 articleId -> 增量。
     * 供定时回刷任务使用。
     */
    public java.util.Map<Long, Long> collectViewIncrs() {
        java.util.Map<Long, Long> map = new java.util.HashMap<>();
        try {
            ScanOptions options = ScanOptions.scanOptions()
                    .match(CacheKeys.ARTICLE_VIEW_INCR + "*").count(200).build();
            try (var cursor = stringRedisTemplate.scan(options)) {
                while (cursor.hasNext()) {
                    String key = cursor.next();
                    String rawId = key.substring(CacheKeys.ARTICLE_VIEW_INCR.length());
                    String v = stringRedisTemplate.opsForValue().get(key);
                    if (v != null) {
                        map.put(Long.parseLong(rawId), Long.parseLong(v));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("扫描浏览增量 key 失败，忽略. msg={}", e.getMessage());
        }
        return map;
    }

    /** 给热门榜加/减分（点赞 +5 / 取消 -5 / 评论 +10 / 浏览 +1） */
    public void zincrHot(Long articleId, double score) {
        try {
            stringRedisTemplate.opsForZSet().incrementScore(CacheKeys.ARTICLE_HOT, String.valueOf(articleId), score);
        } catch (Exception e) {
            log.warn("热门榜加分失败，忽略. articleId={}, score={}, msg={}", articleId, score, e.getMessage());
        }
    }

    /** 热门榜倒序取前 N 个文章 ID */
    public List<Long> zrevrangeHot(long start, long end) {
        try {
            Set<String> set = stringRedisTemplate.opsForZSet().reverseRange(CacheKeys.ARTICLE_HOT, start, end);
            List<Long> result = new ArrayList<>();
            if (set != null) {
                for (String s : set) {
                    result.add(Long.parseLong(s));
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("读取热门榜失败，降级返回空. msg={}", e.getMessage());
            return List.of();
        }
    }
}
