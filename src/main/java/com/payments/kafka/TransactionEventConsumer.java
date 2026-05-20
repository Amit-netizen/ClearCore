package com.payments.kafka;

import com.payments.domain.entity.EventLog;
import com.payments.repository.EventLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TransactionEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventConsumer.class);

    private final EventLogRepository eventLogRepository;

    public TransactionEventConsumer(EventLogRepository eventLogRepository) {
        this.eventLogRepository = eventLogRepository;
    }

    @KafkaListener(topics = TransactionEventPublisher.TOPIC,
                   groupId = "${spring.kafka.consumer.group-id}")
    public void consume(@Payload TransactionEvent event,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                        @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Received {} event: txnId={}, status={}", event.eventType(), event.transactionId(), event.status());

        EventLog entry = EventLog.builder()
                .eventType(event.eventType())
                .transactionId(event.transactionId())
                .payload(Map.of(
                        "eventId", event.eventId().toString(),
                        "eventType", event.eventType(),
                        "transactionId", event.transactionId().toString(),
                        "amount", event.amount(),
                        "status", event.status().name(),
                        "responseCode", event.responseCode().getCode(),
                        "fraudScore", event.fraudScore(),
                        "partition", partition,
                        "offset", offset
                ))
                .processed(true)
                .build();

        eventLogRepository.save(entry);
    }
}
