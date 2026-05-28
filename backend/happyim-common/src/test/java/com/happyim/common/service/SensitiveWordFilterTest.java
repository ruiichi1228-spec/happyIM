package com.happyim.common.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SensitiveWordFilter Unit Tests")
class SensitiveWordFilterTest {

    private final SensitiveWordFilter filter = new SensitiveWordFilter();

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
}
