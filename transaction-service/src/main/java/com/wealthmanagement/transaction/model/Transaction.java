package com.wealthmanagement.transaction.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "transactions")
public class Transaction {

    @Id
    private String id;

    private String portfolioId;
    private String accountNumber;
    private TransactionType type;
    private String symbol;
    private String assetName;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal amount;
    private BigDecimal commission;
    private BigDecimal totalAmount;
    private String currency;
    private TransactionStatus status;
    private String notes;

    @CreatedDate
    private LocalDateTime transactionDate;

    private LocalDateTime processedDate;

    public enum TransactionType {
        BUY, SELL, DIVIDEND, DEPOSIT, WITHDRAWAL
    }

    public enum TransactionStatus {
        PENDING, PROCESSING, COMPLETED, FAILED, CANCELLED
    }

    public void calculateTotalAmount() {
        if (amount != null && commission != null) {
            this.totalAmount = amount.add(commission);
        } else if (amount != null) {
            this.totalAmount = amount;
        }
    }
}
