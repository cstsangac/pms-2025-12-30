package com.wealthmanagement.portfolio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class HoldingDTO {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddRequest {
        @NotBlank(message = "Symbol is required")
        private String symbol;

        @NotBlank(message = "Name is required")
        private String name;

        @NotBlank(message = "Asset type is required")
        private String assetType;

        @NotNull(message = "Quantity is required")
        @Positive(message = "Quantity must be positive")
        private BigDecimal quantity;

        @NotNull(message = "Average cost is required")
        @Positive(message = "Average cost must be positive")
        private BigDecimal averageCost;

        @NotNull(message = "Current price is required")
        @Positive(message = "Current price must be positive")
        private BigDecimal currentPrice;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private String symbol;
        private String name;
        private String assetType;
        private BigDecimal quantity;
        private BigDecimal averageCost;
        private BigDecimal currentPrice;
        private BigDecimal marketValue;
        private BigDecimal unrealizedGainLoss;
        private BigDecimal unrealizedGainLossPercentage;
        private LocalDateTime lastUpdated;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        @NotNull(message = "Quantity is required")
        private BigDecimal quantity;

        @NotNull(message = "Average cost is required")
        private BigDecimal averageCost;

        @NotNull(message = "Current price is required")
        private BigDecimal currentPrice;
    }
}
