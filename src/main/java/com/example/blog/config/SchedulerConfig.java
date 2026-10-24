package com.example.blog.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务开关。
 * 生产默认开启（matchIfMissing=true）。
 * 测试环境在 src/test/resources/application.yml 中设 blog.scheduler.enabled=false，
 * 避免测试期间后台线程干扰浏览量断言。
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "blog.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulerConfig {
}
