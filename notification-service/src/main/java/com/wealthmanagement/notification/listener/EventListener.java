package com.wealthmanagement.notification.listener;

import com.wealthmanagement.notification.event.PortfolioEvent;
import com.wealthmanagement.notification.event.TransactionEvent;
import com.wealthmanagement.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventListener {

    private final NotificationService notificationService;

    @KafkaListener(topics = "${app.kafka.topics.portfolio-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void handlePortfolioEvent(
            @Payload PortfolioEvent event,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Received portfolio event: {} from topic: {}, partition: {}, offset: {}, key: {}",
                event.getEventType(), topic, partition, offset, key);

        try {
            notificationService.processPortfolioEvent(event);
            log.info("Successfully processed portfolio event: {}", event.getEventType());
        } catch (Exception e) {
            log.error("Error processing portfolio event: {}", event.getEventType(), e);
            // In production, implement dead letter queue or retry mechanism
        }
    }

    @KafkaListener(topics = "${app.kafka.topics.transaction-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void handleTransactionEvent(
            @Payload TransactionEvent event,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Received transaction event: {} from topic: {}, partition: {}, offset: {}, key: {}",
                event.getEventType(), topic, partition, offset, key);

        try {
            notificationService.processTransactionEvent(event);
            log.info("Successfully processed transaction event: {}", event.getEventType());
        } catch (Exception e) {
            log.error("Error processing transaction event: {}", event.getEventType(), e);
            // In production, implement dead letter queue or retry mechanism
        }
    }
}
