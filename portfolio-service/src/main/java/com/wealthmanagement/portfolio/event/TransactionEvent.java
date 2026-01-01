package com.wealthmanagement.portfolio.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEvent {

    private EventType eventType;
    private String transactionId;
    private String portfolioId;
    private String accountNumber;
    private TransactionType transactionType;
    private String symbol;
    private String assetName;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal totalAmount;
    private TransactionStatus status;
    private LocalDateTime timestamp;

    public enum EventType {
        TRANSACTION_CREATED,
        TRANSACTION_PROCESSING,
        TRANSACTION_COMPLETED,
        TRANSACTION_FAILED,
        TRANSACTION_CANCELLED
    }

    public enum TransactionType {
        BUY, SELL, DEPOSIT, WITHDRAWAL, DIVIDEND, INTEREST, FEE
    }

    public enum TransactionStatus {
        PENDING, PROCESSING, COMPLETED, FAILED, CANCELLED
    }
}
