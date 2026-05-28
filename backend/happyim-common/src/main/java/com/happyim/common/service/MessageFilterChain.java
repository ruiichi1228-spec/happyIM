package com.happyim.common.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MessageFilterChain {

    private final List<MessageFilter> filters = new ArrayList<>();

    public MessageFilterChain(List<MessageFilter> filters) {
        if (filters != null) this.filters.addAll(filters);
    }

    public String execute(String content) {
        String result = content;
        for (MessageFilter f : filters) {
            result = f.doFilter(result);
        }
        return result;
    }
}
