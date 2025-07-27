package com.aldhafara.astroSpotFinder.service;

import com.aldhafara.astroSpotFinder.model.Coordinate;
import org.springframework.cache.annotation.Cacheable;

public class StraightLineDistanceService implements DistanceService {

    private static final double EARTH_RADIUS_KM = 6371.0;

    @Override
    @Cacheable("distances")
    public double findDistance(Coordinate pointA, Coordinate pointB) {
        double latA = Math.toRadians(pointA.latitude());
        double lonA = Math.toRadians(pointA.longitude());
        double latB = Math.toRadians(pointB.latitude());
        double lonB = Math.toRadians(pointB.longitude());

        double dLat = latB - latA;
        double dLon = lonB - lonA;

        double haversine = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(latA) * Math.cos(latB) * Math.pow(Math.sin(dLon / 2), 2);
        double angleBetweenPoints = 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));

        return EARTH_RADIUS_KM * angleBetweenPoints;
    }
}
