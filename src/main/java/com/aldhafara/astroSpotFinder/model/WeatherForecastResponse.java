package com.aldhafara.astroSpotFinder.model;

import java.util.List;

public record WeatherForecastResponse(
        double latitude,
        double longitude,
        double generationTimeMs,
        int utcOffsetSeconds,
        String timezone,
        String timezoneAbbreviation,
        int elevation,
        HourlyUnits hourlyUnits,
        List<DataPeriod> data
) {}
