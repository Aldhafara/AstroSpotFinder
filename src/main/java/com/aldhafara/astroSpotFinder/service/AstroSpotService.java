package com.aldhafara.astroSpotFinder.service;

import com.aldhafara.astroSpotFinder.model.Coordinate;
import com.aldhafara.astroSpotFinder.model.GridSize;
import com.aldhafara.astroSpotFinder.model.LocationConditions;

import java.util.List;

public interface AstroSpotService {

    List<Coordinate> findPointsWithinRadius(Coordinate center, double radiusKm, GridSize gridSize);
    List<LocationConditions> filterTopByBrightness(List<Coordinate> points);
}
