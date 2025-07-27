package com.aldhafara.astroSpotFinder.service;

import com.aldhafara.astroSpotFinder.model.Coordinate;
import com.aldhafara.astroSpotFinder.model.LightPollutionInfo;

import java.util.Optional;

public interface LightPollutionService {
    Optional<LightPollutionInfo> getLightPollution(Coordinate coordinate);
}
