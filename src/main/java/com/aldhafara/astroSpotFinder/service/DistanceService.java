package com.aldhafara.astroSpotFinder.service;

import com.aldhafara.astroSpotFinder.model.Coordinate;

public interface DistanceService {

    double findDistance(Coordinate pointA, Coordinate pointB);
}
