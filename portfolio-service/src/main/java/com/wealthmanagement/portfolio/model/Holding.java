package com.wealthmanagement.portfolio.model;

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
public class Holding {

    private String symbol;
    private String name;
    private String assetType; // STOCK, BOND, MUTUAL_FUND, ETF, etc.
    private BigDecimal quantity;
    private BigDecimal averageCost;
    private BigDecimal currentPrice;
    private BigDecimal marketValue;
    private BigDecimal unrealizedGainLoss;
    private BigDecimal unrealizedGainLossPercentage;
    private LocalDateTime lastUpdated;

    public void calculateMarketValue() {
        if (quantity != null && currentPrice != null) {
            this.marketValue = quantity.multiply(currentPrice);
        }
    }

    public void calculateUnrealizedGainLoss() {
        if (marketValue != null && averageCost != null && quantity != null) {
            BigDecimal totalCost = averageCost.multiply(quantity);
            this.unrealizedGainLoss = marketValue.subtract(totalCost);
            if (totalCost.compareTo(BigDecimal.ZERO) > 0) {
                this.unrealizedGainLossPercentage = unrealizedGainLoss
                        .divide(totalCost, 4, BigDecimal.ROUND_HALF_UP)
                        .multiply(new BigDecimal("100"));
            }
        }
    }
}
