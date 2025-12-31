package com.wealthmanagement.portfolio.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Value("${app.kafka.topics.portfolio-events}")
    private String portfolioEventsTopic;

    @Bean
    public NewTopic portfolioEventsTopic() {
        return TopicBuilder
                .name(portfolioEventsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
