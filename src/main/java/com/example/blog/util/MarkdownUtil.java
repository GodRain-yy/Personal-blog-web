package com.example.blog.util;

import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

import java.util.List;

/**
 * Markdown 渲染 + HTML 白名单过滤。
 *
 * 安全口径：用户输入一律不可信，Markdown 本身允许内嵌原生 HTML，
 * 因此必须「先渲染，再按白名单重新过滤」，最后落库的 content_html 才是可安全输出的 HTML。
 */
public final class MarkdownUtil {

    private MarkdownUtil() {
    }

    private static final List<org.commonmark.Extension> EXTENSIONS = List.of(TablesExtension.create());

    private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();

    private static final HtmlRenderer RENDERER = HtmlRenderer.builder().extensions(EXTENSIONS).build();

    /**
     * 输出白名单：结构与排版标签 + 代码块 class（语法高亮依赖）+ 图片 + 链接。
     * 刻意不开放 style 属性和 script/iframe，避免 CSS 注入与 XSS。
     */
    private static final PolicyFactory POLICY = Sanitizers.FORMATTING
            .and(Sanitizers.BLOCKS)
            .and(Sanitizers.IMAGES)
            .and(Sanitizers.LINKS)
            .and(Sanitizers.TABLES)
            .and(new HtmlPolicyBuilder()
                    .allowElements("code", "pre", "hr")
                    // 代码语言标识，供 highlight.js 取用
                    .allowAttributes("class").onElements("code", "pre")
                    // 外链重定向防护：新窗口打开必须带 noopener
                    .allowAttributes("target").onElements("a")
                    .toFactory());

    /**
     * Markdown 转安全 HTML。
     *
     * @param markdown Markdown 原文，允许为 null
     * @return 已过滤的 HTML；入参为 null 或空白时返回空串
     */
    public static String renderToHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        String rawHtml = RENDERER.render(PARSER.parse(markdown));
        return POLICY.sanitize(rawHtml);
    }

    /**
     * 生成摘要：先剥离 Markdown 语法符号，再按字数截断。
     */
    public static String extractSummary(String markdown, int maxLength) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        String plain = markdown
                // 去掉代码块
                .replaceAll("(?s)```.*?```", " ")
                .replaceAll("`[^`]*`", " ")
                // 去掉图片与链接，保留文字
                .replaceAll("!\\[[^\\]]*]\\([^)]*\\)", " ")
                .replaceAll("\\[([^\\]]*)]\\([^)]*\\)", "$1")
                // 去掉标题、引用、列表、分隔、加粗斜体等标记
                .replaceAll("(?m)^\\s*[#>\\-*+|]+\\s*", " ")
                .replaceAll("[*_~]{1,3}", "")
                .replaceAll("\\s+", " ")
                .trim();

        if (plain.length() <= maxLength) {
            return plain;
        }
        return plain.substring(0, maxLength) + "…";
    }

    /**
     * 统计正文字数：中文按字符计，英文单词按词计。
     */
    public static int countWords(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return 0;
        }
        String plain = markdown.replaceAll("(?s)```.*?```", " ").replaceAll("\\s+", " ").trim();
        int chinese = 0;
        for (char c : plain.toCharArray()) {
            if (isChinese(c)) {
                chinese++;
            }
        }
        int words = plain.replaceAll("[\\u4e00-\\u9fa5]", " ")
                .trim()
                .isEmpty() ? 0 : plain.replaceAll("[\\u4e00-\\u9fa5]", " ").trim().split("\\s+").length;
        return chinese + words;
    }

    private static boolean isChinese(char c) {
        return c >= '\u4e00' && c <= '\u9fa5';
    }
}
