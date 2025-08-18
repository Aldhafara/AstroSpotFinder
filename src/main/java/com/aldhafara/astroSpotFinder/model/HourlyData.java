package com.aldhafara.astroSpotFinder.model;

public record HourlyData(
        long timestamp,
        String hour,
        double temperature,
        int cloudCover,
        double visibility,
        double windSpeed,
        double windGust
) {}
