package com.aldhafara.astroSpotFinder.model;

public record AggregatedWeatherData(
        LocationConditions locationConditions,
        double avgCloudCover,
        double avgVisibility,
        double maxWindSpeed,
        double maxWindGust,
        double avgTemperature
) {
}
