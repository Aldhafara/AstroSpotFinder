package com.aldhafara.astroSpotFinder.service;

import com.aldhafara.astroSpotFinder.model.Coordinate;
import com.aldhafara.astroSpotFinder.model.GridSize;
import com.aldhafara.astroSpotFinder.model.LocationConditions;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class AstroSpotServiceImpl implements AstroSpotService {

    private static final Logger log = LoggerFactory.getLogger(AstroSpotServiceImpl.class);

    private static final double KM_PER_DEGREE = 111.0;

    private final LightPollutionService lightPollutionService;
    private final DistanceService distanceService;
    private final ExecutorService executorService;
    private final int topNumber;
    private final double topPercent;

    public AstroSpotServiceImpl(LightPollutionService lightPollutionService,
                                DistanceService distanceService,
                                @Value("${astrospot.top.number}") int topNumber,
                                @Value("${astrospot.top.percent}") double topPercent,
                                @Value("${astrospot.executor.threads:4}") int executorThreads) {
        this.lightPollutionService = lightPollutionService;
        this.distanceService = distanceService;
        this.executorService = Executors.newFixedThreadPool(executorThreads);
        this.topNumber = topNumber <= 0 ? 1 : topNumber;
        this.topPercent = topPercent > 100 ? 100 : topPercent;
    }

    private static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
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
                if (log.isDebugEnabled() && coordinates.size() % 10 == 0) {
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

    @Override
    public List<LocationConditions> searchBestSpotsRecursive(Coordinate center, double radiusKm, GridSize gridSize, int depth, int maxDepth, double radiusDiv, int gridDiv) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start("searchBestSpotsRecursive [depth={}]");

        if (depth > maxDepth || radiusKm < 0.04) {
            log.debug("searchBestSpotsRecursive [depth={}]: invalid parameters radiusKm={} depth={} maxDepth={}", depth, radiusKm, depth, maxDepth);
            return Collections.emptyList();
        }

        List<Coordinate> gridPoints = findPointsWithinRadius(center, radiusKm, gridSize);

        if (gridPoints.isEmpty()) {
            log.debug("searchBestSpotsRecursive [depth={}]: list gridPoints is empty", depth);
            return Collections.emptyList();
        }

        List<LocationConditions> topSpots = filterTopByBrightness(gridPoints);

        if (topSpots.isEmpty()) {
            log.debug("searchBestSpotsRecursive [depth={}]: list topSpots is empty", depth);
            return Collections.emptyList();
        }

        List<LocationConditions> result = new ArrayList<>(topSpots);

        List<CompletableFuture<List<LocationConditions>>> futures = topSpots.stream()
                .map(spot -> supplyAsync(() -> {
                    Coordinate subCenter = spot.coordinate();
                    double nextRadius = radiusKm / radiusDiv;
                    GridSize nextGrid = new GridSize(
                            gridSize.latitudeDegrees() / gridDiv,
                            gridSize.longitudeDegrees() / gridDiv
                    );
                    return searchBestSpotsRecursive(subCenter, nextRadius, nextGrid,
                            depth + 1, maxDepth, radiusDiv, gridDiv);
                }))
                .toList();

        List<LocationConditions> subResults = futures.stream()
                .map(future -> {
                    try {
                        return future.join();
                    } catch (CompletionException e) {
                        log.error("Exception in recursive task", e);
                        return Collections.<LocationConditions>emptyList();
                    }
                })
                .flatMap(List::stream)
                .toList();

        result.addAll(subResults.stream()
                .filter(distinctByKey(loc -> List.of(loc.coordinate(), loc.brightness())))
                .toList());

        stopWatch.stop();
        log.info("searchBestSpotsRecursive finished at depth={} in {} ms", depth, stopWatch.getTotalTimeMillis());

        return result;
    }

    protected <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, executorService);
    }

    @PreDestroy
    public void shutdownExecutor() {
        executorService.shutdown();
    }
}
