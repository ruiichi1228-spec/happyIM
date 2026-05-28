package com.happyim.common.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SensitiveWordFilter Unit Tests")
class SensitiveWordFilterTest {

    private final SensitiveWordFilter filter = new SensitiveWordFilter(null);

    @Test
    @DisplayName("should replace sensitive word")
    void shouldReplaceSensitiveWord() {
        String result = filter.doFilter("这是广告内容");
        assertEquals("这是***内容", result);
    }

    @Test
    @DisplayName("should handle multiple matches")
    void shouldHandleMultipleMatches() {
        String result = filter.doFilter("发广告和违禁词");
        assertFalse(result.contains("广告"));
        assertFalse(result.contains("违禁词"));
    }

    @Test
    @DisplayName("should return unchanged if no match")
    void shouldReturnUnchanged() {
        String result = filter.doFilter("正常聊天内容");
        assertEquals("正常聊天内容", result);
    }

    @Test
    @DisplayName("should handle empty input")
    void shouldHandleEmptyInput() {
        assertEquals("", filter.doFilter(""));
    }

    @Test
    @DisplayName("should filter http URL")
    void shouldFilterHttpUrl() {
        String result = filter.doFilter("看看这个 http://example.com/path 链接");
        assertFalse(result.contains("http://"));
        assertFalse(result.contains("example.com"));
        assertTrue(result.contains("***"));
    }

    @Test
    @DisplayName("should filter https URL")
    void shouldFilterHttpsUrl() {
        String result = filter.doFilter("打开 https://www.baidu.com 试试");
        assertFalse(result.contains("https://"));
        assertFalse(result.contains("baidu.com"));
        assertTrue(result.contains("***"));
    }

    @Test
    @DisplayName("should filter bare domain")
    void shouldFilterBareDomain() {
        String result = filter.doFilter("上 evil.com 看看");
        assertFalse(result.contains("evil.com"));
        assertTrue(result.contains("***"));
    }

    @Test
    @DisplayName("should filter URL and sensitive word together")
    void shouldFilterUrlAndSensitiveWord() {
        String result = filter.doFilter("广告链接 http://bad.com/page");
        assertFalse(result.contains("广告"));
        assertFalse(result.contains("http://"));
        assertFalse(result.contains("bad.com"));
    }

    @Test
    @DisplayName("should not filter normal text with dots")
    void shouldNotFilterNormalDots() {
        String result = filter.doFilter("版本号是 3.14.0 发布了");
        assertEquals("版本号是 3.14.0 发布了", result);
    }

    @Test
    @DisplayName("should filter URL with port")
    void shouldFilterUrlWithPort() {
        String result = filter.doFilter("访问 http://localhost:8080/api");
        assertFalse(result.contains("http://"));
        assertFalse(result.contains("localhost:8080"));
        assertTrue(result.contains("***"));
    }
}
