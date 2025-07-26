package com.aldhafara.astroSpotFinder.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record Coordinate(
        @Min(-90) @Max(90) double latitude,
        @Min(-180) @Max(180) double longitude) {
}
