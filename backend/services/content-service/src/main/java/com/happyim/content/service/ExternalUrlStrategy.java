package com.happyim.content.service;

import org.springframework.stereotype.Component;

@Component
public class ExternalUrlStrategy implements UrlResolutionStrategy {
    @Override
    public String resolve(String path) {
        return path; // 完整 URL，直接返回
    }
}
