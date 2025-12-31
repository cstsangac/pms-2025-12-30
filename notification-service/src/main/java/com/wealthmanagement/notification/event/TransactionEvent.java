package com.wealthmanagement.notification.event;

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
    private String transactionType;
    private String symbol;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal totalAmount;
    private String status;
    private LocalDateTime timestamp;

    public enum EventType {
        TRANSACTION_CREATED,
        TRANSACTION_PROCESSING,
        TRANSACTION_COMPLETED,
        TRANSACTION_FAILED,
        TRANSACTION_CANCELLED
    }
}
