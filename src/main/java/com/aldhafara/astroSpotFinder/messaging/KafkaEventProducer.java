package com.aldhafara.astroSpotFinder.messaging;

public interface KafkaEventProducer<T> {
    void sendEvent(T event);
}
