package com.smartcare.icustats.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

@Configuration
public class DataCenterMongoConfig {

    @Value("${mongodb.datacenter.uri}")
    private String dataCenterUri;

    @Value("${mongodb.timeout-ms:10000}")
    private int timeoutMs;

    @Bean
    public SimpleMongoClientDatabaseFactory dataCenterMongoFactory() {
        ConnectionString connectionString = new ConnectionString(dataCenterUri);
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .applyToSocketSettings(builder -> builder.connectTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS))
                .applyToClusterSettings(builder -> builder.serverSelectionTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS))
                .build();
        return new SimpleMongoClientDatabaseFactory(settings);
    }

    @Bean
    public MongoTemplate dataCenterMongoTemplate() {
        return new MongoTemplate(dataCenterMongoFactory());
    }
}
