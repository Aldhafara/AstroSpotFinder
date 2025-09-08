package com.aldhafara.astroSpotFinder.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = false)
public class LightPollutionErrorKafkaProducer implements KafkaEventProducer<LightPollutionErrorEvent> {

    private static final Logger log = LoggerFactory.getLogger(LightPollutionErrorKafkaProducer.class);

    private final KafkaTemplate<String, LightPollutionErrorEvent> kafkaTemplate;
    private static final String TOPIC = "light-pollution-errors";

    public LightPollutionErrorKafkaProducer(KafkaTemplate<String, LightPollutionErrorEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void sendEvent(LightPollutionErrorEvent event) {
        try {
            kafkaTemplate.send(TOPIC, event);
        } catch (Exception e) {
            log.error("Failed to send LightPollutionErrorEvent to Kafka", e);
        }
    }
}
