package com.aldhafara.astroSpotFinder.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HourlyData(
        long timestamp,
        String hour,
        double temperature,
        @JsonProperty("cloudcover")
        int cloudCover,
        double visibility,
        @JsonProperty("windspeed")
        double windSpeed,
        @JsonProperty("windgust")
        double windGust
) {}
