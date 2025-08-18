package com.aldhafara.astroSpotFinder.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record WeatherForecastResponse(
        double latitude,
        double longitude,
        @JsonProperty("generationtime_ms")
        double generationTimeMs,
        @JsonProperty("utc_offset_seconds")
        int utcOffsetSeconds,
        String timezone,
        @JsonProperty("timezone_abbreviation")
        String timezoneAbbreviation,
        int elevation,
        @JsonProperty("hourly_units")
        HourlyUnits hourlyUnits,
        List<DataPeriod> data
) {}
