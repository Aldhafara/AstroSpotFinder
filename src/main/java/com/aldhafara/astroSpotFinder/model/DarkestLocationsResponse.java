package com.aldhafara.astroSpotFinder.model;

import java.util.List;

public record DarkestLocationsResponse(
        String additionalMessage,
        List<LocationsCluster> locationsCluster
) {}
