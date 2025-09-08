package com.aldhafara.astroSpotFinder.messaging;

import com.aldhafara.astroSpotFinder.model.Coordinate;

import java.time.Instant;

public record LightPollutionErrorEvent(
        Coordinate coordinate,
        int httpStatusCode,
        Instant timestamp,
        String errorMessage
) {}
