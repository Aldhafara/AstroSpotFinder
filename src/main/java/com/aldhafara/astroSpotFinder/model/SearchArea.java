package com.aldhafara.astroSpotFinder.model;

import lombok.Builder;

@Builder
public record SearchArea(
        Coordinate center,
        double radiusKm) {
}
