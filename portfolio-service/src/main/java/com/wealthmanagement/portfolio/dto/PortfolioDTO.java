package com.wealthmanagement.portfolio.dto;

import com.wealthmanagement.portfolio.model.Portfolio;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class PortfolioDTO {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotBlank(message = "Client ID is required")
        private String clientId;

        @NotBlank(message = "Client name is required")
        private String clientName;

        @NotBlank(message = "Account number is required")
        private String accountNumber;

        @NotBlank(message = "Currency is required")
        private String currency;

        @NotNull(message = "Initial cash balance is required")
        @Positive(message = "Cash balance must be positive")
        private BigDecimal cashBalance;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private String id;
        private String clientId;
        private String clientName;
        private String accountNumber;
        private String currency;
        private BigDecimal totalValue;
        private BigDecimal cashBalance;
        private List<HoldingDTO.Response> holdings;
        private Portfolio.PortfolioStatus status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        private BigDecimal cashBalance;
        private Portfolio.PortfolioStatus status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private String id;
        private String clientId;
        private String clientName;
        private String accountNumber;
        private BigDecimal totalValue;
        private BigDecimal cashBalance;
        private Integer holdingsCount;
        private Portfolio.PortfolioStatus status;
    }
}
