package com.example.blog.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MarkdownUtil 自检，重点覆盖 D3 完成判据：危险内容必须被白名单剥离。
 */
class MarkdownUtilTest {

    @Test
    void shouldStripScriptTag() {
        String md = "# 标题\n\n<script>alert('xss')</script>\n\n正文内容";
        String html = MarkdownUtil.renderToHtml(md);

        assertFalse(html.contains("<script"), "script 标签必须被剥离: " + html);
        assertFalse(html.contains("alert"), "脚本内容必须被清除: " + html);
        assertTrue(html.contains("正文内容"), "正常正文应保留: " + html);
    }

    @Test
    void shouldStripEventHandlers() {
        String md = "<img src=\"x\" onerror=\"alert(1)\" />\n\n正常段落";
        String html = MarkdownUtil.renderToHtml(md);

        assertFalse(html.contains("onerror"), "事件处理器属性必须被剥离: " + html);
        assertTrue(html.contains("正常段落"), "正常正文应保留: " + html);
    }

    @Test
    void shouldStripInlineStyle() {
        String md = "<div style=\"position:fixed;top:0\">伪装层</div>";
        String html = MarkdownUtil.renderToHtml(md);

        assertFalse(html.contains("style="), "style 属性应被剥离以规避 CSS 注入: " + html);
    }

    @Test
    void shouldKeepCommonMarkdownSyntax() {
        String md = """
                # 一级标题

                ```java
                int a = 1;
                ```

                | 列1 | 列2 |
                |----|----|
                | A  | B  |

                [链接](https://example.com)
                """;
        String html = MarkdownUtil.renderToHtml(md);

        assertTrue(html.contains("<h1>"), "标题应渲染: " + html);
        assertTrue(html.contains("<code"), "代码块应渲染: " + html);
        assertTrue(html.contains("<table>"), "表格应渲染: " + html);
        assertTrue(html.contains("<a href=\"https://example.com\""), "链接应渲染: " + html);
    }

    @Test
    void shouldExtractSummaryWithoutMarkdownSyntax() {
        String md = "# 标题标题\n\n这是第一段正文内容，用于验证摘要提取。";
        String summary = MarkdownUtil.extractSummary(md, 20);

        assertFalse(summary.contains("#"), "摘要不应包含 Markdown 标记: " + summary);
        assertTrue(summary.contains("这是第一段正文"), "摘要应包含正文: " + summary);
        assertTrue(summary.length() <= 21, "摘要应被截断到指定长度: " + summary);
    }

    @Test
    void shouldCountChineseAndEnglishWords() {
        assertTrue(MarkdownUtil.countWords("中文四个字") >= 4, "中文应按字符计数");
        assertTrue(MarkdownUtil.countWords("hello world java") >= 3, "英文应按词计数");
        assertEquals(0, MarkdownUtil.countWords(null), "null 应返回 0");
    }

    @Test
    void shouldHandleBlankInput() {
        assertEquals("", MarkdownUtil.renderToHtml(null));
        assertEquals("", MarkdownUtil.renderToHtml("   "));
        assertEquals("", MarkdownUtil.extractSummary(null, 100));
    }
}
