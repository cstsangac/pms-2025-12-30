package com.wealthmanagement.notification.service;

import com.wealthmanagement.notification.event.PortfolioEvent;
import com.wealthmanagement.notification.event.TransactionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NotificationService {

    public void processPortfolioEvent(PortfolioEvent event) {
        log.info("Processing portfolio event: {}", event.getEventType());

        String notification = buildPortfolioNotification(event);
        sendNotification(event.getClientId(), notification);
    }

    public void processTransactionEvent(TransactionEvent event) {
        log.info("Processing transaction event: {}", event.getEventType());

        String notification = buildTransactionNotification(event);
        sendNotification(event.getAccountNumber(), notification);
    }

    private String buildPortfolioNotification(PortfolioEvent event) {
        return switch (event.getEventType()) {
            case PORTFOLIO_CREATED ->
                    String.format("Portfolio %s created successfully for client %s",
                            event.getPortfolioId(), event.getClientId());
            case PORTFOLIO_UPDATED ->
                    String.format("Portfolio %s updated. Total value: $%s",
                            event.getPortfolioId(), event.getTotalValue());
            case PORTFOLIO_DELETED ->
                    String.format("Portfolio %s has been deleted", event.getPortfolioId());
            case HOLDING_ADDED ->
                    String.format("New holding added to portfolio %s. Total value: $%s",
                            event.getPortfolioId(), event.getTotalValue());
            case HOLDING_UPDATED ->
                    String.format("Holding updated in portfolio %s. Total value: $%s",
                            event.getPortfolioId(), event.getTotalValue());
            case HOLDING_REMOVED ->
                    String.format("Holding removed from portfolio %s. Total value: $%s",
                            event.getPortfolioId(), event.getTotalValue());
        };
    }

    private String buildTransactionNotification(TransactionEvent event) {
        return switch (event.getEventType()) {
            case TRANSACTION_CREATED ->
                    String.format("Transaction %s created: %s %s shares of %s at $%s",
                            event.getTransactionId(), event.getTransactionType(),
                            event.getQuantity(), event.getSymbol(), event.getPrice());
            case TRANSACTION_PROCESSING ->
                    String.format("Transaction %s is being processed", event.getTransactionId());
            case TRANSACTION_COMPLETED ->
                    String.format("Transaction %s completed successfully. Total: $%s",
                            event.getTransactionId(), event.getTotalAmount());
            case TRANSACTION_FAILED ->
                    String.format("Transaction %s failed. Please contact support.",
                            event.getTransactionId());
            case TRANSACTION_CANCELLED ->
                    String.format("Transaction %s has been cancelled", event.getTransactionId());
        };
    }

    private void sendNotification(String recipient, String message) {
        // In a real implementation, this would send emails, SMS, push notifications, etc.
        log.info("===== NOTIFICATION =====");
        log.info("To: {}", recipient);
        log.info("Message: {}", message);
        log.info("========================");

        // Simulate notification delivery
        // Could integrate with services like:
        // - SendGrid/AWS SES for email
        // - Twilio for SMS
        // - Firebase Cloud Messaging for push notifications
        // - WebSocket for real-time notifications
    }
}
