package com.wealthmanagement.portfolio.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "portfolios")
public class Portfolio {

    @Id
    private String id;

    private String clientId;
    private String clientName;
    private String accountNumber;
    private String currency;

    @Builder.Default
    private BigDecimal totalValue = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal cashBalance = BigDecimal.ZERO;

    @Builder.Default
    private List<Holding> holdings = new ArrayList<>();

    private PortfolioStatus status;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public enum PortfolioStatus {
        ACTIVE, INACTIVE, SUSPENDED, CLOSED
    }
}
