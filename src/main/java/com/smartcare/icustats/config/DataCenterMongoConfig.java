package com.smartcare.icustats.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

@Configuration
public class DataCenterMongoConfig {

    @Value("${mongodb.datacenter.uri}")
    private String dataCenterUri;

    @Bean
    @Primary
    public SimpleMongoClientDatabaseFactory dataCenterMongoFactory() {
        return new SimpleMongoClientDatabaseFactory(dataCenterUri);
    }

    @Bean
    @Primary
    public MongoTemplate dataCenterMongoTemplate() {
        return new MongoTemplate(dataCenterMongoFactory());
    }
}
