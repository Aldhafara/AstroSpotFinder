package com.aldhafara.astroSpotFinder.model;

import java.util.Optional;

public record SimplifiedLocationConditions(
        Coordinate coordinate,
        double brightness,
        HourlyUnits hourlyUnits,
        Optional<DataPeriod> data,
        Double score) {
}
