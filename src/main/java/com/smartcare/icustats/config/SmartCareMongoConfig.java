package com.smartcare.icustats.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

@Configuration
public class SmartCareMongoConfig {

    @Value("${mongodb.smartcare.uri}")
    private String smartCareUri;

    @Value("${mongodb.timeout-ms:10000}")
    private int timeoutMs;

    @Bean
    public SimpleMongoClientDatabaseFactory smartCareMongoFactory() {
        ConnectionString connectionString = new ConnectionString(smartCareUri);
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .applyToSocketSettings(builder -> builder.connectTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS))
                .applyToClusterSettings(builder -> builder.serverSelectionTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS))
                .build();
        return new SimpleMongoClientDatabaseFactory(settings);
    }

    @Bean
    public MongoTemplate smartCareMongoTemplate() {
        return new MongoTemplate(smartCareMongoFactory());
    }
}
