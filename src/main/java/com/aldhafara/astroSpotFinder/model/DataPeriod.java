package com.aldhafara.astroSpotFinder.model;

import java.util.List;

public record DataPeriod(
        String period,
        double moonIllumination,
        List<HourlyData> hours
) {}
