package com.wealthmanagement.transaction.controller;

import com.wealthmanagement.transaction.dto.TransactionDTO;
import com.wealthmanagement.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@RequestMapping("/transactions")
@RequiredArgsConstructor
@Tag(name = "Transaction Management", description = "APIs for managing buy/sell transactions")
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    @Operation(summary = "Create a new transaction", description = "Creates and processes a new transaction")
    public ResponseEntity<TransactionDTO.Response> createTransaction(
            @Valid @RequestBody TransactionDTO.CreateRequest request) {
        log.info("REST request to create transaction for portfolio: {}", request.getPortfolioId());
        TransactionDTO.Response response = transactionService.createTransaction(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get transaction by ID")
    public ResponseEntity<TransactionDTO.Response> getTransactionById(
            @Parameter(description = "Transaction ID") @PathVariable String id) {
        log.info("REST request to get transaction: {}", id);
        TransactionDTO.Response response = transactionService.getTransactionById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/portfolio/{portfolioId}")
    @Operation(summary = "Get transactions by portfolio ID")
    public ResponseEntity<List<TransactionDTO.Response>> getTransactionsByPortfolioId(
            @Parameter(description = "Portfolio ID") @PathVariable String portfolioId) {
        log.info("REST request to get transactions for portfolio: {}", portfolioId);
        List<TransactionDTO.Response> responses = transactionService.getTransactionsByPortfolioId(portfolioId);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/account/{accountNumber}")
    @Operation(summary = "Get transactions by account number")
    public ResponseEntity<List<TransactionDTO.Response>> getTransactionsByAccountNumber(
            @Parameter(description = "Account number") @PathVariable String accountNumber) {
        log.info("REST request to get transactions for account: {}", accountNumber);
        List<TransactionDTO.Response> responses = transactionService.getTransactionsByAccountNumber(accountNumber);
        return ResponseEntity.ok(responses);
    }

    @GetMapping
    @Operation(summary = "Get all transactions")
    public ResponseEntity<List<TransactionDTO.Response>> getAllTransactions() {
        log.info("REST request to get all transactions");
        List<TransactionDTO.Response> responses = transactionService.getAllTransactions();
        return ResponseEntity.ok(responses);
    }

    @PutMapping("/{id}/cancel")
    @Operation(summary = "Cancel a transaction")
    public ResponseEntity<TransactionDTO.Response> cancelTransaction(
            @Parameter(description = "Transaction ID") @PathVariable String id) {
        log.info("REST request to cancel transaction: {}", id);
        TransactionDTO.Response response = transactionService.cancelTransaction(id);
        return ResponseEntity.ok(response);
    }
}
