package com.wealthmanagement.transaction.event;

import com.wealthmanagement.transaction.model.Transaction;
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
    private Transaction.TransactionType transactionType;
    private String symbol;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal totalAmount;
    private Transaction.TransactionStatus status;
    private LocalDateTime timestamp;

    public enum EventType {
        TRANSACTION_CREATED,
        TRANSACTION_PROCESSING,
        TRANSACTION_COMPLETED,
        TRANSACTION_FAILED,
        TRANSACTION_CANCELLED
    }
}
