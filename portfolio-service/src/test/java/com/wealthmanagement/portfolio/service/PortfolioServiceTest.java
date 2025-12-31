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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Portfolio Service Tests")
class PortfolioServiceTest {

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private PortfolioMapper portfolioMapper;

    @Mock
    private KafkaTemplate<String, PortfolioEvent> kafkaTemplate;

    @InjectMocks
    private PortfolioService portfolioService;

    private PortfolioDTO.CreateRequest createRequest;
    private Portfolio portfolio;
    private PortfolioDTO.Response portfolioResponse;

    @BeforeEach
    void setUp() {
        createRequest = PortfolioDTO.CreateRequest.builder()
                .clientId("CLIENT123")
                .clientName("John Doe")
                .accountNumber("ACC001")
                .currency("USD")
                .cashBalance(new BigDecimal("100000.00"))
                .build();

        portfolio = Portfolio.builder()
                .id("PORT001")
                .clientId("CLIENT123")
                .clientName("John Doe")
                .accountNumber("ACC001")
                .currency("USD")
                .cashBalance(new BigDecimal("100000.00"))
                .totalValue(new BigDecimal("100000.00"))
                .holdings(new ArrayList<>())
                .status(Portfolio.PortfolioStatus.ACTIVE)
                .build();

        portfolioResponse = PortfolioDTO.Response.builder()
                .id("PORT001")
                .clientId("CLIENT123")
                .clientName("John Doe")
                .accountNumber("ACC001")
                .currency("USD")
                .cashBalance(new BigDecimal("100000.00"))
                .totalValue(new BigDecimal("100000.00"))
                .status(Portfolio.PortfolioStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("Should create portfolio successfully")
    void shouldCreatePortfolioSuccessfully() {
        // Given
        when(portfolioRepository.existsByAccountNumber(createRequest.getAccountNumber())).thenReturn(false);
        when(portfolioMapper.toEntity(createRequest)).thenReturn(portfolio);
        when(portfolioRepository.save(any(Portfolio.class))).thenReturn(portfolio);
        when(portfolioMapper.toResponse(portfolio)).thenReturn(portfolioResponse);

        // When
        PortfolioDTO.Response result = portfolioService.createPortfolio(createRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getClientId()).isEqualTo("CLIENT123");
        assertThat(result.getAccountNumber()).isEqualTo("ACC001");
        verify(portfolioRepository).save(any(Portfolio.class));
        verify(kafkaTemplate).send(eq("portfolio-events"), eq("PORT001"), any(PortfolioEvent.class));
    }

    @Test
    @DisplayName("Should throw exception when portfolio already exists")
    void shouldThrowExceptionWhenPortfolioExists() {
        // Given
        when(portfolioRepository.existsByAccountNumber(createRequest.getAccountNumber())).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> portfolioService.createPortfolio(createRequest))
                .isInstanceOf(ResourceAlreadyExistsException.class)
                .hasMessageContaining("already exists");

        verify(portfolioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should get portfolio by ID successfully")
    void shouldGetPortfolioByIdSuccessfully() {
        // Given
        when(portfolioRepository.findById("PORT001")).thenReturn(Optional.of(portfolio));
        when(portfolioMapper.toResponse(portfolio)).thenReturn(portfolioResponse);

        // When
        PortfolioDTO.Response result = portfolioService.getPortfolioById("PORT001");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("PORT001");
        verify(portfolioRepository).findById("PORT001");
    }

    @Test
    @DisplayName("Should throw exception when portfolio not found")
    void shouldThrowExceptionWhenPortfolioNotFound() {
        // Given
        when(portfolioRepository.findById("INVALID")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> portfolioService.getPortfolioById("INVALID"))
                .isInstanceOf(PortfolioNotFoundException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("Should add holding to portfolio successfully")
    void shouldAddHoldingSuccessfully() {
        // Given
        HoldingDTO.AddRequest holdingRequest = HoldingDTO.AddRequest.builder()
                .symbol("AAPL")
                .name("Apple Inc.")
                .assetType("STOCK")
                .quantity(new BigDecimal("100"))
                .averageCost(new BigDecimal("150.00"))
                .currentPrice(new BigDecimal("155.00"))
                .build();

        Holding holding = Holding.builder()
                .symbol("AAPL")
                .name("Apple Inc.")
                .assetType("STOCK")
                .quantity(new BigDecimal("100"))
                .averageCost(new BigDecimal("150.00"))
                .currentPrice(new BigDecimal("155.00"))
                .build();

        when(portfolioRepository.findById("PORT001")).thenReturn(Optional.of(portfolio));
        when(portfolioMapper.toHoldingEntity(holdingRequest)).thenReturn(holding);
        when(portfolioRepository.save(any(Portfolio.class))).thenReturn(portfolio);
        when(portfolioMapper.toResponse(portfolio)).thenReturn(portfolioResponse);

        // When
        PortfolioDTO.Response result = portfolioService.addHolding("PORT001", holdingRequest);

        // Then
        assertThat(result).isNotNull();
        verify(portfolioRepository).save(any(Portfolio.class));
        verify(kafkaTemplate).send(eq("portfolio-events"), eq("PORT001"), any(PortfolioEvent.class));
    }

    @Test
    @DisplayName("Should update portfolio successfully")
    void shouldUpdatePortfolioSuccessfully() {
        // Given
        PortfolioDTO.UpdateRequest updateRequest = PortfolioDTO.UpdateRequest.builder()
                .cashBalance(new BigDecimal("120000.00"))
                .status(Portfolio.PortfolioStatus.ACTIVE)
                .build();

        when(portfolioRepository.findById("PORT001")).thenReturn(Optional.of(portfolio));
        when(portfolioRepository.save(any(Portfolio.class))).thenReturn(portfolio);
        when(portfolioMapper.toResponse(portfolio)).thenReturn(portfolioResponse);

        // When
        PortfolioDTO.Response result = portfolioService.updatePortfolio("PORT001", updateRequest);

        // Then
        assertThat(result).isNotNull();
        verify(portfolioMapper).updateEntityFromRequest(updateRequest, portfolio);
        verify(portfolioRepository).save(portfolio);
    }

    @Test
    @DisplayName("Should delete portfolio successfully")
    void shouldDeletePortfolioSuccessfully() {
        // Given
        when(portfolioRepository.findById("PORT001")).thenReturn(Optional.of(portfolio));
        doNothing().when(portfolioRepository).delete(portfolio);

        // When
        portfolioService.deletePortfolio("PORT001");

        // Then
        verify(portfolioRepository).delete(portfolio);
        verify(kafkaTemplate).send(eq("portfolio-events"), eq("PORT001"), any(PortfolioEvent.class));
    }

    @Test
    @DisplayName("Should get portfolios by client ID")
    void shouldGetPortfoliosByClientId() {
        // Given
        List<Portfolio> portfolios = List.of(portfolio);
        List<PortfolioDTO.Response> responses = List.of(portfolioResponse);

        when(portfolioRepository.findByClientId("CLIENT123")).thenReturn(portfolios);
        when(portfolioMapper.toResponseList(portfolios)).thenReturn(responses);

        // When
        List<PortfolioDTO.Response> result = portfolioService.getPortfoliosByClientId("CLIENT123");

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getClientId()).isEqualTo("CLIENT123");
    }
}
