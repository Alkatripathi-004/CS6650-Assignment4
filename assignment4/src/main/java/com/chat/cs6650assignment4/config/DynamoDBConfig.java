package com.chat.cs6650assignment4.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.time.Duration;

@Configuration
public class DynamoDBConfig {

    @Value("${aws.dynamodb.region:us-east-1}")
    private String region;

    @Bean
    public DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .httpClient(ApacheHttpClient.builder()
                        .maxConnections(100)        // Matches your Thread Pool size
                        .connectionTimeout(Duration.ofMillis(2000)) // Fast fail on connection
                        .build())
                .build();
    }
}