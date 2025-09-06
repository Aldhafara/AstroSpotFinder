package com.aldhafara.astroSpotFinder.model;

import java.util.Map;

public record LocationConditions(
        Coordinate coordinate,
        double brightness,
        WeatherForecastResponse weather,
        Map<String, Double> score) implements Comparable<LocationConditions> {

    @Override
    public int compareTo(LocationConditions other) {
        return Double.compare(this.brightness, other.brightness);
    }
}
