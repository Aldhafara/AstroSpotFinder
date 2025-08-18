package com.aldhafara.astroSpotFinder.model;

public record HourlyUnits(
        String time,
        String cloudCover,
        String temperature2m,
        String visibility,
        String windSpeed10m,
        String windGusts10m
) {}
