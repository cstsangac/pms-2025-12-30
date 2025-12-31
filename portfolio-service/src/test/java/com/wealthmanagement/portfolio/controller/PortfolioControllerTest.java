package com.wealthmanagement.portfolio.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wealthmanagement.portfolio.dto.PortfolioDTO;
import com.wealthmanagement.portfolio.model.Portfolio;
import com.wealthmanagement.portfolio.service.PortfolioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PortfolioController.class)
@DisplayName("Portfolio Controller Tests")
class PortfolioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PortfolioService portfolioService;

    private PortfolioDTO.CreateRequest createRequest;
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
    @DisplayName("POST /portfolios - Should create portfolio successfully")
    void shouldCreatePortfolioSuccessfully() throws Exception {
        // Given
        when(portfolioService.createPortfolio(any(PortfolioDTO.CreateRequest.class)))
                .thenReturn(portfolioResponse);

        // When & Then
        mockMvc.perform(post("/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("PORT001"))
                .andExpect(jsonPath("$.clientId").value("CLIENT123"))
                .andExpect(jsonPath("$.accountNumber").value("ACC001"));
    }

    @Test
    @DisplayName("POST /portfolios - Should return 400 for invalid request")
    void shouldReturn400ForInvalidRequest() throws Exception {
        // Given
        PortfolioDTO.CreateRequest invalidRequest = PortfolioDTO.CreateRequest.builder()
                .clientId("")  // Invalid: empty
                .build();

        // When & Then
        mockMvc.perform(post("/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /portfolios/{id} - Should get portfolio by ID successfully")
    void shouldGetPortfolioByIdSuccessfully() throws Exception {
        // Given
        when(portfolioService.getPortfolioById("PORT001")).thenReturn(portfolioResponse);

        // When & Then
        mockMvc.perform(get("/portfolios/PORT001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("PORT001"))
                .andExpect(jsonPath("$.clientId").value("CLIENT123"));
    }

    @Test
    @DisplayName("GET /portfolios/client/{clientId} - Should get portfolios by client ID")
    void shouldGetPortfoliosByClientId() throws Exception {
        // Given
        List<PortfolioDTO.Response> responses = List.of(portfolioResponse);
        when(portfolioService.getPortfoliosByClientId("CLIENT123")).thenReturn(responses);

        // When & Then
        mockMvc.perform(get("/portfolios/client/CLIENT123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].clientId").value("CLIENT123"));
    }

    @Test
    @DisplayName("PUT /portfolios/{id} - Should update portfolio successfully")
    void shouldUpdatePortfolioSuccessfully() throws Exception {
        // Given
        PortfolioDTO.UpdateRequest updateRequest = PortfolioDTO.UpdateRequest.builder()
                .cashBalance(new BigDecimal("120000.00"))
                .status(Portfolio.PortfolioStatus.ACTIVE)
                .build();

        when(portfolioService.updatePortfolio(eq("PORT001"), any(PortfolioDTO.UpdateRequest.class)))
                .thenReturn(portfolioResponse);

        // When & Then
        mockMvc.perform(put("/portfolios/PORT001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("PORT001"));
    }

    @Test
    @DisplayName("DELETE /portfolios/{id} - Should delete portfolio successfully")
    void shouldDeletePortfolioSuccessfully() throws Exception {
        // When & Then
        mockMvc.perform(delete("/portfolios/PORT001"))
                .andExpect(status().isNoContent());
    }
}
