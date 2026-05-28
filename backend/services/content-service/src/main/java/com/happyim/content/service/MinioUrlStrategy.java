package com.happyim.content.service;

import com.happyim.common.config.MinioConfig;
import org.springframework.stereotype.Component;

@Component
public class MinioUrlStrategy implements UrlResolutionStrategy {

    private final String endpoint;

    public MinioUrlStrategy(MinioConfig config) {
        this.endpoint = config.getEndpoint();
    }

    @Override
    public String resolve(String path) {
        return endpoint + "/" + path;
    }
}
