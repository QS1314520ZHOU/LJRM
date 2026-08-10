package com.smartcare.icustats.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

@Configuration
public class SmartCareMongoConfig {

    @Value("${mongodb.smartcare.uri}")
    private String smartCareUri;

    @Bean
    public SimpleMongoClientDatabaseFactory smartCareMongoFactory() {
        return new SimpleMongoClientDatabaseFactory(smartCareUri);
    }

    @Bean
    public MongoTemplate smartCareMongoTemplate() {
        return new MongoTemplate(smartCareMongoFactory());
    }
}
