package com.aldhafara.javaMicroserviceStarter.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Service
public class StatusService {
    private final Instant startTime = Instant.now();

    public long getUptimeMillis() {
        return Duration.between(startTime, Instant.now()).toMillis();
    }

    public String getTimestamp() {
        return DateTimeFormatter.ISO_INSTANT.format(Instant.now().atZone(ZoneOffset.UTC));
    }
}
