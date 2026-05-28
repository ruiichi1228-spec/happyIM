package com.happyim.chat.service;

/**
 * 消息过滤器接口。Chain of Responsibility 模式。
 * 每个 Filter 可以修改或拦截消息。
 */
public interface MessageFilter {
    String doFilter(String content) throws FilterRejectException;

    class FilterRejectException extends RuntimeException {
        public FilterRejectException(String msg) { super(msg); }
    }
}
