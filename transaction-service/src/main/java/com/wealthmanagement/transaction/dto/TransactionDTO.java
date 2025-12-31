package com.wealthmanagement.transaction.dto;

import com.wealthmanagement.transaction.model.Transaction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TransactionDTO {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotBlank(message = "Portfolio ID is required")
        private String portfolioId;

        @NotBlank(message = "Account number is required")
        private String accountNumber;

        @NotNull(message = "Transaction type is required")
        private Transaction.TransactionType type;

        @NotBlank(message = "Symbol is required")
        private String symbol;

        @NotBlank(message = "Asset name is required")
        private String assetName;

        @NotNull(message = "Quantity is required")
        @Positive(message = "Quantity must be positive")
        private BigDecimal quantity;

        @NotNull(message = "Price is required")
        @Positive(message = "Price must be positive")
        private BigDecimal price;

        @NotBlank(message = "Currency is required")
        private String currency;

        private BigDecimal commission;
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private String id;
        private String portfolioId;
        private String accountNumber;
        private Transaction.TransactionType type;
        private String symbol;
        private String assetName;
        private BigDecimal quantity;
        private BigDecimal price;
        private BigDecimal amount;
        private BigDecimal commission;
        private BigDecimal totalAmount;
        private String currency;
        private Transaction.TransactionStatus status;
        private String notes;
        private LocalDateTime transactionDate;
        private LocalDateTime processedDate;
    }
}
