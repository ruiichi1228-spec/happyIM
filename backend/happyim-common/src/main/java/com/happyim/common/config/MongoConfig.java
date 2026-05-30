package com.happyim.common.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.index.Index;

import java.util.concurrent.TimeUnit;

@Configuration
public class MongoConfig {

    @Value("${spring.data.mongodb.uri:mongodb://localhost:27017/happyim}")
    private String mongoUri;

    @Bean
    public MongoDatabaseFactory mongoDatabaseFactory() {
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(mongoUri))
                .applyToSocketSettings(b -> b
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS))
                .applyToConnectionPoolSettings(b -> b
                        .maxSize(20)
                        .minSize(2)
                        .maxWaitTime(5, TimeUnit.SECONDS))
                .applyToServerSettings(b -> b
                        .heartbeatFrequency(10, TimeUnit.SECONDS)
                        .minHeartbeatFrequency(2, TimeUnit.SECONDS))
                .build();
        return new SimpleMongoClientDatabaseFactory(MongoClients.create(settings), "happyim");
    }

    @Bean
    public MongoTemplate mongoTemplate(MongoDatabaseFactory factory) {
        MongoTemplate template = new MongoTemplate(factory);

        template.indexOps("messages")
                .ensureIndex(new Index().on("messageId", Sort.Direction.ASC).unique());

        template.indexOps("messages")
                .ensureIndex(new Index().on("conversationId", Sort.Direction.ASC)
                        .on("messageId", Sort.Direction.DESC));

        template.indexOps("message_feed")
                .ensureIndex(new Index().on("userId", Sort.Direction.ASC)
                        .on("conversationId", Sort.Direction.ASC)
                        .on("messageId", Sort.Direction.DESC));

        return template;
    }
}
