package com.aldhafara.astroSpotFinder.service;

import com.aldhafara.astroSpotFinder.model.Coordinate;
import com.aldhafara.astroSpotFinder.model.GridSize;
import com.aldhafara.astroSpotFinder.model.LocationConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class AstroSpotServiceImpl implements AstroSpotService {

    private static final Logger log = LoggerFactory.getLogger(AstroSpotServiceImpl.class);

    private static final double KM_PER_DEGREE = 111.0;

    private final LightPollutionService lightPollutionService;
    private final DistanceService distanceService;
    private final int topNumber;
    private final double topPercent;

    public AstroSpotServiceImpl(LightPollutionService lightPollutionService,
                                DistanceService distanceService,
                                @Value("${astrospot.top.number}") int topNumber,
                                @Value("${astrospot.top.percent}") double topPercent) {
        this.lightPollutionService = lightPollutionService;
        this.distanceService = distanceService;
        this.topNumber = topNumber <= 0 ? 1 : topNumber;
        this.topPercent = topPercent > 100 ? 100 : topPercent;
    }

    @Override
    public List<Coordinate> findPointsWithinRadius(Coordinate center, double radiusKm, GridSize gridSize) {
        log.debug("findPointsWithinRadius called with center={} radiusKm={} gridSize={}", center, radiusKm, gridSize);

        List<Coordinate> coordinates = new ArrayList<>();

        if (radiusKm <= 0 || center == null) {
            log.warn("findPointsWithinRadius: invalid parameters radiusKm={} center={}", radiusKm, center);
            return coordinates;
        }

        double centerLat = center.latitude();
        double centerLon = center.longitude();

        double radiusInDegrees = radiusKm / KM_PER_DEGREE;

        double minLat = centerLat - radiusInDegrees;
        double maxLat = centerLat + radiusInDegrees;
        double minLon = centerLon - radiusInDegrees;
        double maxLon = centerLon + radiusInDegrees;

        for (double lat = minLat; lat <= maxLat; lat += gridSize.latitudeDegrees()) {
            for (double lon = minLon; lon <= maxLon; lon += gridSize.longitudeDegrees()) {
                double distance = distanceService.findDistance(new Coordinate(centerLat, centerLon), new Coordinate(lat, lon));
                if (distance <= radiusKm) {
                    coordinates.add(new Coordinate(lat, lon));
                }
                if (log.isDebugEnabled() && coordinates.size()%10 == 0) {
                    log.debug("Generated {} points within radius {} km from center {}", coordinates.size(), radiusKm, center);
                }
            }
        }
        if (coordinates.size() > 1000) {
            log.warn("Large number of points generated ({}) - consider tuning gridSize or radius.", coordinates.size());
        }

        log.debug("Generated {} points within radius {} km from center {}", coordinates.size(), radiusKm, center);
        return coordinates;
    }

    @Override
    public List<LocationConditions> filterTopByBrightness(List<Coordinate> coordinates) {
        if (coordinates == null || coordinates.isEmpty()) {
            log.info("There is no coordinates to check.");
            return Collections.emptyList();
        }
        if (topPercent <= 0) {
            log.warn("The percentage of coordinates examined is set below 0. Top {} coordinates will be taken into account.", topNumber);
            return Collections.emptyList();
        }
        if (topPercent > 50) {
            log.warn("The percentage of coordinates examined is set to high ({}%). This may negatively impact performance.", topPercent);
        }

        List<LocationConditions> brightnessList = coordinates.stream()
                .map(coord -> lightPollutionService.getLightPollution(coord)
                        .map(info -> new LocationConditions(coord, info.relativeBrightness()))
                        .orElse(null))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(LocationConditions::brightness))
                .collect(Collectors.toList());

        int limit = (int) Math.ceil(brightnessList.size() * (topPercent / 100.0));

        int finalSize = Math.min(Math.max(limit, topNumber), brightnessList.size());
        log.debug("filterTopByBrightness() return {} of {} coordinates", finalSize, coordinates.size());
        return brightnessList.subList(0, finalSize);
    }
}
