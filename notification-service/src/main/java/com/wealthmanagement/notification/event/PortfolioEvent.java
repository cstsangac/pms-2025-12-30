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
public class PortfolioEvent {

    private EventType eventType;
    private String portfolioId;
    private String clientId;
    private String accountNumber;
    private BigDecimal totalValue;
    private LocalDateTime timestamp;

    public enum EventType {
        PORTFOLIO_CREATED,
        PORTFOLIO_UPDATED,
        PORTFOLIO_DELETED,
        HOLDING_ADDED,
        HOLDING_UPDATED,
        HOLDING_REMOVED
    }
}
