package com.wealthmanagement.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("portfolio-swagger", r -> r
                        .path("/portfolio-api-docs/**")
                        .filters(f -> f.rewritePath("/portfolio-api-docs/(?<segment>.*)",
                                "/api/portfolio/v3/api-docs/${segment}"))
                        .uri("http://localhost:8081"))
                .route("transaction-swagger", r -> r
                        .path("/transaction-api-docs/**")
                        .filters(f -> f.rewritePath("/transaction-api-docs/(?<segment>.*)",
                                "/api/transaction/v3/api-docs/${segment}"))
                        .uri("http://localhost:8082"))
                .build();
    }
}
