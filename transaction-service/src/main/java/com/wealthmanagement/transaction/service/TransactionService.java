package com.wealthmanagement.transaction.service;

import com.wealthmanagement.transaction.dto.TransactionDTO;
import com.wealthmanagement.transaction.event.TransactionEvent;
import com.wealthmanagement.transaction.exception.TransactionNotFoundException;
import com.wealthmanagement.transaction.mapper.TransactionMapper;
import com.wealthmanagement.transaction.model.Transaction;
import com.wealthmanagement.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;
    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;

    private static final String TRANSACTION_EVENTS_TOPIC = "transaction-events";

    @Transactional
    public TransactionDTO.Response createTransaction(TransactionDTO.CreateRequest request) {
        log.info("Creating transaction for portfolio: {}", request.getPortfolioId());

        Transaction transaction = transactionMapper.toEntity(request);

        // Calculate amount and total
        BigDecimal amount = request.getQuantity().multiply(request.getPrice());
        transaction.setAmount(amount);

        if (request.getCommission() != null) {
            transaction.setCommission(request.getCommission());
        } else {
            transaction.setCommission(BigDecimal.ZERO);
        }

        transaction.calculateTotalAmount();
        transaction.setStatus(Transaction.TransactionStatus.PENDING);

        Transaction saved = transactionRepository.save(transaction);
        log.info("Transaction created with ID: {}", saved.getId());

        publishEvent(TransactionEvent.EventType.TRANSACTION_CREATED, saved);

        // Automatically process the transaction
        return processTransaction(saved.getId());
    }

    @Transactional
    public TransactionDTO.Response processTransaction(String transactionId) {
        log.info("Processing transaction: {}", transactionId);

        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found: " + transactionId));

        if (transaction.getStatus() != Transaction.TransactionStatus.PENDING) {
            log.warn("Transaction {} is not in PENDING status", transactionId);
            return transactionMapper.toResponse(transaction);
        }

        transaction.setStatus(Transaction.TransactionStatus.PROCESSING);
        transactionRepository.save(transaction);

        publishEvent(TransactionEvent.EventType.TRANSACTION_PROCESSING, transaction);

        // Simulate processing
        try {
            // In real scenario, this would integrate with trading systems
            Thread.sleep(100);

            transaction.setStatus(Transaction.TransactionStatus.COMPLETED);
            transaction.setProcessedDate(LocalDateTime.now());

            Transaction completed = transactionRepository.save(transaction);
            log.info("Transaction processed successfully: {}", transactionId);

            publishEvent(TransactionEvent.EventType.TRANSACTION_COMPLETED, completed);

            return transactionMapper.toResponse(completed);

        } catch (Exception e) {
            log.error("Failed to process transaction: {}", transactionId, e);
            transaction.setStatus(Transaction.TransactionStatus.FAILED);
            transactionRepository.save(transaction);

            publishEvent(TransactionEvent.EventType.TRANSACTION_FAILED, transaction);

            throw new RuntimeException("Transaction processing failed", e);
        }
    }

    public TransactionDTO.Response getTransactionById(String id) {
        log.debug("Fetching transaction: {}", id);
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found: " + id));
        return transactionMapper.toResponse(transaction);
    }

    public List<TransactionDTO.Response> getTransactionsByPortfolioId(String portfolioId) {
        log.debug("Fetching transactions for portfolio: {}", portfolioId);
        List<Transaction> transactions = transactionRepository.findByPortfolioId(portfolioId);
        return transactionMapper.toResponseList(transactions);
    }

    public List<TransactionDTO.Response> getTransactionsByAccountNumber(String accountNumber) {
        log.debug("Fetching transactions for account: {}", accountNumber);
        List<Transaction> transactions = transactionRepository.findByAccountNumber(accountNumber);
        return transactionMapper.toResponseList(transactions);
    }

    public List<TransactionDTO.Response> getAllTransactions() {
        log.debug("Fetching all transactions");
        List<Transaction> transactions = transactionRepository.findAll();
        return transactionMapper.toResponseList(transactions);
    }

    @Transactional
    public TransactionDTO.Response cancelTransaction(String transactionId) {
        log.info("Cancelling transaction: {}", transactionId);

        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found: " + transactionId));

        if (transaction.getStatus() == Transaction.TransactionStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel completed transaction");
        }

        transaction.setStatus(Transaction.TransactionStatus.CANCELLED);
        Transaction cancelled = transactionRepository.save(transaction);

        publishEvent(TransactionEvent.EventType.TRANSACTION_CANCELLED, cancelled);

        return transactionMapper.toResponse(cancelled);
    }

    private void publishEvent(TransactionEvent.EventType eventType, Transaction transaction) {
        TransactionEvent event = TransactionEvent.builder()
                .eventType(eventType)
                .transactionId(transaction.getId())
                .portfolioId(transaction.getPortfolioId())
                .accountNumber(transaction.getAccountNumber())
                .transactionType(transaction.getType())
                .symbol(transaction.getSymbol())
                .quantity(transaction.getQuantity())
                .price(transaction.getPrice())
                .totalAmount(transaction.getTotalAmount())
                .status(transaction.getStatus())
                .timestamp(LocalDateTime.now())
                .build();

        kafkaTemplate.send(TRANSACTION_EVENTS_TOPIC, transaction.getId(), event);
        log.debug("Published event: {} for transaction: {}", eventType, transaction.getId());
    }
}
