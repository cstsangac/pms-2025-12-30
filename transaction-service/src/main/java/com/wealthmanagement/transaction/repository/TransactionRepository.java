package com.wealthmanagement.transaction.repository;

import com.wealthmanagement.transaction.model.Transaction;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionRepository extends MongoRepository<Transaction, String> {

    List<Transaction> findByPortfolioId(String portfolioId);

    List<Transaction> findByAccountNumber(String accountNumber);

    List<Transaction> findByStatus(Transaction.TransactionStatus status);

    List<Transaction> findByPortfolioIdAndTransactionDateBetween(
            String portfolioId, LocalDateTime startDate, LocalDateTime endDate);

    List<Transaction> findByTransactionDateBetween(LocalDateTime startDate, LocalDateTime endDate);
}
