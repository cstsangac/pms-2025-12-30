package com.wealthmanagement.portfolio.controller;

import com.wealthmanagement.portfolio.dto.HoldingDTO;
import com.wealthmanagement.portfolio.dto.PortfolioDTO;
import com.wealthmanagement.portfolio.service.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/portfolios")
@RequiredArgsConstructor
@Tag(name = "Portfolio Management", description = "APIs for managing client portfolios and holdings")
public class PortfolioController {

    private final PortfolioService portfolioService;

    @PostMapping
    @Operation(summary = "Create a new portfolio", description = "Creates a new portfolio for a client")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Portfolio created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "409", description = "Portfolio already exists")
    })
    public ResponseEntity<PortfolioDTO.Response> createPortfolio(
            @Valid @RequestBody PortfolioDTO.CreateRequest request) {
        log.info("REST request to create portfolio for client: {}", request.getClientId());
        PortfolioDTO.Response response = portfolioService.createPortfolio(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get portfolio by ID", description = "Retrieves a portfolio by its unique identifier")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Portfolio found"),
            @ApiResponse(responseCode = "404", description = "Portfolio not found")
    })
    public ResponseEntity<PortfolioDTO.Response> getPortfolioById(
            @Parameter(description = "Portfolio ID") @PathVariable String id) {
        log.info("REST request to get portfolio: {}", id);
        PortfolioDTO.Response response = portfolioService.getPortfolioById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/account/{accountNumber}")
    @Operation(summary = "Get portfolio by account number", description = "Retrieves a portfolio by account number")
    public ResponseEntity<PortfolioDTO.Response> getPortfolioByAccountNumber(
            @Parameter(description = "Account number") @PathVariable String accountNumber) {
        log.info("REST request to get portfolio by account: {}", accountNumber);
        PortfolioDTO.Response response = portfolioService.getPortfolioByAccountNumber(accountNumber);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/client/{clientId}")
    @Operation(summary = "Get portfolios by client ID", description = "Retrieves all portfolios for a specific client")
    public ResponseEntity<List<PortfolioDTO.Response>> getPortfoliosByClientId(
            @Parameter(description = "Client ID") @PathVariable String clientId) {
        log.info("REST request to get portfolios for client: {}", clientId);
        List<PortfolioDTO.Response> responses = portfolioService.getPortfoliosByClientId(clientId);
        return ResponseEntity.ok(responses);
    }

    @GetMapping
    @Operation(summary = "Get all portfolios summary", description = "Retrieves a summary of all portfolios")
    public ResponseEntity<List<PortfolioDTO.Summary>> getAllPortfoliosSummary() {
        log.info("REST request to get all portfolios summary");
        List<PortfolioDTO.Summary> summaries = portfolioService.getAllPortfoliosSummary();
        return ResponseEntity.ok(summaries);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update portfolio", description = "Updates an existing portfolio")
    public ResponseEntity<PortfolioDTO.Response> updatePortfolio(
            @Parameter(description = "Portfolio ID") @PathVariable String id,
            @Valid @RequestBody PortfolioDTO.UpdateRequest request) {
        log.info("REST request to update portfolio: {}", id);
        PortfolioDTO.Response response = portfolioService.updatePortfolio(id, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{portfolioId}/holdings")
    @Operation(summary = "Add holding to portfolio", description = "Adds a new holding to an existing portfolio")
    public ResponseEntity<PortfolioDTO.Response> addHolding(
            @Parameter(description = "Portfolio ID") @PathVariable String portfolioId,
            @Valid @RequestBody HoldingDTO.AddRequest request) {
        log.info("REST request to add holding to portfolio: {}", portfolioId);
        PortfolioDTO.Response response = portfolioService.addHolding(portfolioId, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{portfolioId}/holdings/{symbol}")
    @Operation(summary = "Update holding", description = "Updates an existing holding in a portfolio")
    public ResponseEntity<PortfolioDTO.Response> updateHolding(
            @Parameter(description = "Portfolio ID") @PathVariable String portfolioId,
            @Parameter(description = "Stock symbol") @PathVariable String symbol,
            @Valid @RequestBody HoldingDTO.UpdateRequest request) {
        log.info("REST request to update holding {} in portfolio: {}", symbol, portfolioId);
        PortfolioDTO.Response response = portfolioService.updateHolding(portfolioId, symbol, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{portfolioId}/holdings/{symbol}")
    @Operation(summary = "Remove holding from portfolio", description = "Removes a holding from a portfolio")
    public ResponseEntity<PortfolioDTO.Response> removeHolding(
            @Parameter(description = "Portfolio ID") @PathVariable String portfolioId,
            @Parameter(description = "Stock symbol") @PathVariable String symbol) {
        log.info("REST request to remove holding {} from portfolio: {}", symbol, portfolioId);
        PortfolioDTO.Response response = portfolioService.removeHolding(portfolioId, symbol);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete portfolio", description = "Deletes a portfolio")
    public ResponseEntity<Void> deletePortfolio(
            @Parameter(description = "Portfolio ID") @PathVariable String id) {
        log.info("REST request to delete portfolio: {}", id);
        portfolioService.deletePortfolio(id);
        return ResponseEntity.noContent().build();
    }
}
