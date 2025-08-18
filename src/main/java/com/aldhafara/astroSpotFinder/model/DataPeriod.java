package com.aldhafara.astroSpotFinder.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record DataPeriod(
        String period,
        @JsonProperty("moon_illumination")
        double moonIllumination,
        List<HourlyData> hours
) {}
