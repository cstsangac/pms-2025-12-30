package com.wealthmanagement.portfolio.service;

import com.wealthmanagement.portfolio.dto.HoldingDTO;
import com.wealthmanagement.portfolio.dto.PortfolioDTO;
import com.wealthmanagement.portfolio.event.PortfolioEvent;
import com.wealthmanagement.portfolio.exception.PortfolioNotFoundException;
import com.wealthmanagement.portfolio.exception.ResourceAlreadyExistsException;
import com.wealthmanagement.portfolio.mapper.PortfolioMapper;
import com.wealthmanagement.portfolio.model.Holding;
import com.wealthmanagement.portfolio.model.Portfolio;
import com.wealthmanagement.portfolio.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioMapper portfolioMapper;
    private final KafkaTemplate<String, PortfolioEvent> kafkaTemplate;

    private static final String PORTFOLIO_EVENTS_TOPIC = "portfolio-events";

    @Transactional
    public PortfolioDTO.Response createPortfolio(PortfolioDTO.CreateRequest request) {
        log.info("Creating portfolio for client: {}", request.getClientId());

        if (portfolioRepository.existsByAccountNumber(request.getAccountNumber())) {
            throw new ResourceAlreadyExistsException("Portfolio with account number " + request.getAccountNumber() + " already exists");
        }

        Portfolio portfolio = portfolioMapper.toEntity(request);
        portfolio.setTotalValue(request.getCashBalance());
        portfolio.setStatus(Portfolio.PortfolioStatus.ACTIVE);

        Portfolio saved = portfolioRepository.save(portfolio);
        log.info("Portfolio created successfully with ID: {}", saved.getId());

        publishEvent(PortfolioEvent.EventType.PORTFOLIO_CREATED, saved);

        return portfolioMapper.toResponse(saved);
    }

    @Cacheable(value = "portfolios", key = "#id")
    public PortfolioDTO.Response getPortfolioById(String id) {
        log.debug("Fetching portfolio with ID: {}", id);
        Portfolio portfolio = portfolioRepository.findById(id)
                .orElseThrow(() -> new PortfolioNotFoundException("Portfolio not found with ID: " + id));
        return portfolioMapper.toResponse(portfolio);
    }

    public PortfolioDTO.Response getPortfolioByAccountNumber(String accountNumber) {
        log.debug("Fetching portfolio with account number: {}", accountNumber);
        Portfolio portfolio = portfolioRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new PortfolioNotFoundException("Portfolio not found with account number: " + accountNumber));
        return portfolioMapper.toResponse(portfolio);
    }

    public List<PortfolioDTO.Response> getPortfoliosByClientId(String clientId) {
        log.debug("Fetching portfolios for client: {}", clientId);
        List<Portfolio> portfolios = portfolioRepository.findByClientId(clientId);
        return portfolioMapper.toResponseList(portfolios);
    }

    public List<PortfolioDTO.Summary> getAllPortfoliosSummary() {
        log.debug("Fetching all portfolios summary");
        List<Portfolio> portfolios = portfolioRepository.findAll();
        return portfolios.stream()
                .map(portfolioMapper::toSummary)
                .toList();
    }

    @Transactional
    @CacheEvict(value = "portfolios", key = "#id")
    public PortfolioDTO.Response updatePortfolio(String id, PortfolioDTO.UpdateRequest request) {
        log.info("Updating portfolio with ID: {}", id);

        Portfolio portfolio = portfolioRepository.findById(id)
                .orElseThrow(() -> new PortfolioNotFoundException("Portfolio not found with ID: " + id));

        portfolioMapper.updateEntityFromRequest(request, portfolio);
        recalculateTotalValue(portfolio);

        Portfolio updated = portfolioRepository.save(portfolio);
        log.info("Portfolio updated successfully: {}", id);

        publishEvent(PortfolioEvent.EventType.PORTFOLIO_UPDATED, updated);

        return portfolioMapper.toResponse(updated);
    }

    @Transactional
    @CacheEvict(value = "portfolios", key = "#portfolioId")
    public PortfolioDTO.Response addHolding(String portfolioId, HoldingDTO.AddRequest request) {
        log.info("Adding holding {} to portfolio: {}", request.getSymbol(), portfolioId);

        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new PortfolioNotFoundException("Portfolio not found with ID: " + portfolioId));

        Holding holding = portfolioMapper.toHoldingEntity(request);
        holding.calculateMarketValue();
        holding.calculateUnrealizedGainLoss();

        portfolio.getHoldings().add(holding);
        recalculateTotalValue(portfolio);

        Portfolio updated = portfolioRepository.save(portfolio);
        log.info("Holding added successfully to portfolio: {}", portfolioId);

        publishEvent(PortfolioEvent.EventType.HOLDING_ADDED, updated);

        return portfolioMapper.toResponse(updated);
    }

    @Transactional
    @CacheEvict(value = "portfolios", key = "#portfolioId")
    public PortfolioDTO.Response updateHolding(String portfolioId, String symbol, HoldingDTO.UpdateRequest request) {
        log.info("Updating holding {} in portfolio: {}", symbol, portfolioId);

        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new PortfolioNotFoundException("Portfolio not found with ID: " + portfolioId));

        Holding holding = portfolio.getHoldings().stream()
                .filter(h -> h.getSymbol().equals(symbol))
                .findFirst()
                .orElseThrow(() -> new PortfolioNotFoundException("Holding not found with symbol: " + symbol));

        portfolioMapper.updateHoldingFromRequest(request, holding);
        holding.calculateMarketValue();
        holding.calculateUnrealizedGainLoss();

        recalculateTotalValue(portfolio);

        Portfolio updated = portfolioRepository.save(portfolio);
        log.info("Holding updated successfully in portfolio: {}", portfolioId);

        publishEvent(PortfolioEvent.EventType.HOLDING_UPDATED, updated);

        return portfolioMapper.toResponse(updated);
    }

    @Transactional
    @CacheEvict(value = "portfolios", key = "#portfolioId")
    public PortfolioDTO.Response removeHolding(String portfolioId, String symbol) {
        log.info("Removing holding {} from portfolio: {}", symbol, portfolioId);

        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new PortfolioNotFoundException("Portfolio not found with ID: " + portfolioId));

        boolean removed = portfolio.getHoldings().removeIf(h -> h.getSymbol().equals(symbol));

        if (!removed) {
            throw new PortfolioNotFoundException("Holding not found with symbol: " + symbol);
        }

        recalculateTotalValue(portfolio);

        Portfolio updated = portfolioRepository.save(portfolio);
        log.info("Holding removed successfully from portfolio: {}", portfolioId);

        publishEvent(PortfolioEvent.EventType.HOLDING_REMOVED, updated);

        return portfolioMapper.toResponse(updated);
    }

    @Transactional
    @CacheEvict(value = "portfolios", key = "#id")
    public void deletePortfolio(String id) {
        log.info("Deleting portfolio with ID: {}", id);

        Portfolio portfolio = portfolioRepository.findById(id)
                .orElseThrow(() -> new PortfolioNotFoundException("Portfolio not found with ID: " + id));

        portfolioRepository.delete(portfolio);
        log.info("Portfolio deleted successfully: {}", id);

        publishEvent(PortfolioEvent.EventType.PORTFOLIO_DELETED, portfolio);
    }

    private void recalculateTotalValue(Portfolio portfolio) {
        BigDecimal holdingsValue = portfolio.getHoldings().stream()
                .map(Holding::getMarketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        portfolio.setTotalValue(portfolio.getCashBalance().add(holdingsValue));
    }

    private void publishEvent(PortfolioEvent.EventType eventType, Portfolio portfolio) {
        PortfolioEvent event = PortfolioEvent.builder()
                .eventType(eventType)
                .portfolioId(portfolio.getId())
                .clientId(portfolio.getClientId())
                .accountNumber(portfolio.getAccountNumber())
                .totalValue(portfolio.getTotalValue())
                .timestamp(LocalDateTime.now())
                .build();

        kafkaTemplate.send(PORTFOLIO_EVENTS_TOPIC, portfolio.getId(), event);
        log.debug("Published event: {} for portfolio: {}", eventType, portfolio.getId());
    }
}
