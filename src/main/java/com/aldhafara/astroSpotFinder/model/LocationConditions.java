package com.aldhafara.astroSpotFinder.model;

import java.util.Map;

public record LocationConditions(
        Coordinate coordinate,
        double brightness,
        WeatherForecastResponse weather,
        Map<String, Double> score) {
}
