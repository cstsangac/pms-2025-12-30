package com.wealthmanagement.portfolio.repository;

import com.wealthmanagement.portfolio.model.Portfolio;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioRepository extends MongoRepository<Portfolio, String> {

    Optional<Portfolio> findByAccountNumber(String accountNumber);

    List<Portfolio> findByClientId(String clientId);

    List<Portfolio> findByStatus(Portfolio.PortfolioStatus status);

    boolean existsByAccountNumber(String accountNumber);
}
