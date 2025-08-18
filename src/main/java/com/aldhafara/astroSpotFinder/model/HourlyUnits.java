package com.aldhafara.astroSpotFinder.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HourlyUnits(
        String time,
        String cloudCover,
        @JsonProperty("temperature_2m")
        String temperature2m,
        String visibility,
        @JsonProperty("windspeed_10m")
        String windSpeed10m,
        @JsonProperty("windgusts_10m")
        String windGusts10m
) {}
