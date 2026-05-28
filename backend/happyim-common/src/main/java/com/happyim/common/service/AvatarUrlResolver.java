package com.happyim.common.service;

import org.springframework.stereotype.Component;

@Component
public class AvatarUrlResolver {

    private final ExternalUrlStrategy externalUrlStrategy;
    private final MinioUrlStrategy minioUrlStrategy;

    public AvatarUrlResolver(ExternalUrlStrategy externalUrlStrategy, MinioUrlStrategy minioUrlStrategy) {
        this.externalUrlStrategy = externalUrlStrategy;
        this.minioUrlStrategy = minioUrlStrategy;
    }

    /**
     * 根据路径选择策略解析完整 URL。
     * 如果是完整 URL（http/https 开头）→ 直接返回；
     * 否则用 MinIO 路径拼接。
     */
    public String resolve(String path) {
        if (path == null || path.isBlank()) return null;
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return externalUrlStrategy.resolve(path);
        }
        return minioUrlStrategy.resolve(path);
    }
}
