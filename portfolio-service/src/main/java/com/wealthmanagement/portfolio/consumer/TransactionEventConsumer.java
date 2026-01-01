package com.wealthmanagement.portfolio.consumer;

import com.wealthmanagement.portfolio.event.TransactionEvent;
import com.wealthmanagement.portfolio.model.Holding;
import com.wealthmanagement.portfolio.model.Portfolio;
import com.wealthmanagement.portfolio.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventConsumer {

    private final PortfolioRepository portfolioRepository;

    @KafkaListener(
            topics = "transaction-events",
            groupId = "portfolio-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @CacheEvict(value = "portfolios", key = "#event.portfolioId")
    public void handleTransactionEvent(TransactionEvent event) {
        log.info("Received transaction event: type={}, transactionId={}, portfolioId={}", 
                event.getEventType(), event.getTransactionId(), event.getPortfolioId());

        // Only process completed transactions
        if (event.getEventType() != TransactionEvent.EventType.TRANSACTION_COMPLETED) {
            log.debug("Ignoring non-completed transaction event: {}", event.getEventType());
            return;
        }

        try {
            processTransactionEvent(event);
        } catch (Exception e) {
            log.error("Error processing transaction event: {}", event.getTransactionId(), e);
            // In production, this should go to a dead-letter queue or retry mechanism
        }
    }

    private void processTransactionEvent(TransactionEvent event) {
        Portfolio portfolio = portfolioRepository.findById(event.getPortfolioId())
                .orElseThrow(() -> new RuntimeException("Portfolio not found: " + event.getPortfolioId()));

        log.info("Processing {} transaction for portfolio {}: {} {} @ {}",
                event.getTransactionType(), event.getPortfolioId(),
                event.getQuantity(), event.getSymbol(), event.getPrice());

        switch (event.getTransactionType()) {
            case BUY:
                processBuyTransaction(portfolio, event);
                break;
            case SELL:
                processSellTransaction(portfolio, event);
                break;
            case DEPOSIT:
                processDepositTransaction(portfolio, event);
                break;
            case WITHDRAWAL:
                processWithdrawalTransaction(portfolio, event);
                break;
            case DIVIDEND:
            case INTEREST:
                processCashTransaction(portfolio, event);
                break;
            default:
                log.warn("Unhandled transaction type: {}", event.getTransactionType());
        }

        portfolioRepository.save(portfolio);
        log.info("Portfolio {} updated successfully after {} transaction",
                portfolio.getId(), event.getTransactionType());
    }

    private void processBuyTransaction(Portfolio portfolio, TransactionEvent event) {
        // Update or create holding
        Optional<Holding> existingHolding = portfolio.getHoldings().stream()
                .filter(h -> h.getSymbol().equals(event.getSymbol()))
                .findFirst();

        if (existingHolding.isPresent()) {
            // Update existing holding - calculate new average cost
            Holding holding = existingHolding.get();
            BigDecimal currentQuantity = holding.getQuantity();
            BigDecimal currentAverageCost = holding.getAverageCost();
            BigDecimal newQuantity = event.getQuantity();
            BigDecimal newPrice = event.getPrice();

            // Calculate weighted average cost
            BigDecimal totalCost = currentQuantity.multiply(currentAverageCost)
                    .add(newQuantity.multiply(newPrice));
            BigDecimal totalQuantity = currentQuantity.add(newQuantity);
            BigDecimal newAverageCost = totalCost.divide(totalQuantity, 2, BigDecimal.ROUND_HALF_UP);

            holding.setQuantity(totalQuantity);
            holding.setAverageCost(newAverageCost);
            holding.setCurrentPrice(newPrice);
            holding.setLastUpdated(LocalDateTime.now());
            holding.calculateMarketValue();
            holding.calculateUnrealizedGainLoss();

            log.debug("Updated holding {}: quantity {} -> {}, avg cost {} -> {}",
                    event.getSymbol(), currentQuantity, totalQuantity, currentAverageCost, newAverageCost);
        } else {
            // Create new holding
            Holding newHolding = Holding.builder()
                    .symbol(event.getSymbol())
                    .name(event.getAssetName())
                    .assetType("STOCK")
                    .quantity(event.getQuantity())
                    .averageCost(event.getPrice())
                    .currentPrice(event.getPrice())
                    .lastUpdated(LocalDateTime.now())
                    .build();
            newHolding.calculateMarketValue();
            newHolding.calculateUnrealizedGainLoss();
            portfolio.getHoldings().add(newHolding);

            log.debug("Created new holding {}: {} @ {}", event.getSymbol(), event.getQuantity(), event.getPrice());
        }

        // Update cash balance (reduce by total amount)
        BigDecimal newCashBalance = portfolio.getCashBalance().subtract(event.getTotalAmount());
        portfolio.setCashBalance(newCashBalance);

        // Recalculate total value
        recalculateTotalValue(portfolio);
    }

    private void processSellTransaction(Portfolio portfolio, TransactionEvent event) {
        Optional<Holding> existingHolding = portfolio.getHoldings().stream()
                .filter(h -> h.getSymbol().equals(event.getSymbol()))
                .findFirst();

        if (existingHolding.isPresent()) {
            Holding holding = existingHolding.get();
            BigDecimal newQuantity = holding.getQuantity().subtract(event.getQuantity());

            if (newQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                // Remove holding if quantity is zero or negative
                portfolio.getHoldings().remove(holding);
                log.debug("Removed holding {} (sold all)", event.getSymbol());
            } else {
                holding.setQuantity(newQuantity);
                holding.setCurrentPrice(event.getPrice());
                holding.setLastUpdated(LocalDateTime.now());
                holding.calculateMarketValue();
                holding.calculateUnrealizedGainLoss();
                log.debug("Updated holding {}: quantity reduced to {}", event.getSymbol(), newQuantity);
            }

            // Update cash balance (increase by total amount)
            BigDecimal newCashBalance = portfolio.getCashBalance().add(event.getTotalAmount());
            portfolio.setCashBalance(newCashBalance);

            recalculateTotalValue(portfolio);
        } else {
            log.error("Cannot sell - holding not found for symbol: {}", event.getSymbol());
        }
    }

    private void processDepositTransaction(Portfolio portfolio, TransactionEvent event) {
        BigDecimal newCashBalance = portfolio.getCashBalance().add(event.getTotalAmount());
        portfolio.setCashBalance(newCashBalance);
        recalculateTotalValue(portfolio);
        log.debug("Processed deposit: {} -> cash balance: {}", event.getTotalAmount(), newCashBalance);
    }

    private void processWithdrawalTransaction(Portfolio portfolio, TransactionEvent event) {
        BigDecimal newCashBalance = portfolio.getCashBalance().subtract(event.getTotalAmount());
        portfolio.setCashBalance(newCashBalance);
        recalculateTotalValue(portfolio);
        log.debug("Processed withdrawal: {} -> cash balance: {}", event.getTotalAmount(), newCashBalance);
    }

    private void processCashTransaction(Portfolio portfolio, TransactionEvent event) {
        BigDecimal newCashBalance = portfolio.getCashBalance().add(event.getTotalAmount());
        portfolio.setCashBalance(newCashBalance);
        recalculateTotalValue(portfolio);
        log.debug("Processed cash transaction ({}): {} -> cash balance: {}",
                event.getTransactionType(), event.getTotalAmount(), newCashBalance);
    }

    private void recalculateTotalValue(Portfolio portfolio) {
        BigDecimal holdingsValue = portfolio.getHoldings().stream()
                .map(Holding::getMarketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalValue = portfolio.getCashBalance().add(holdingsValue);
        portfolio.setTotalValue(totalValue);

        log.debug("Recalculated portfolio value: cash={}, holdings={}, total={}",
                portfolio.getCashBalance(), holdingsValue, totalValue);
    }
}
