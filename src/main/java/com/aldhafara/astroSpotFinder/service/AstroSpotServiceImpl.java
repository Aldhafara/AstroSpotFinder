package com.aldhafara.astroSpotFinder.service;

import com.aldhafara.astroSpotFinder.model.Coordinate;
import com.aldhafara.astroSpotFinder.model.GridSize;
import com.aldhafara.astroSpotFinder.model.LocationConditions;
import com.aldhafara.astroSpotFinder.model.SearchArea;
import com.aldhafara.astroSpotFinder.model.SearchContext;
import com.aldhafara.astroSpotFinder.model.SearchParams;
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
                                @Value("${astrospot.top.percent}") double topPercent) {
        this.lightPollutionService = lightPollutionService;
        this.distanceService = distanceService;
        this.executorService = Executors.newCachedThreadPool();
        this.topNumber = topNumber <= 0 ? 1 : topNumber;
        this.topPercent = topPercent > 100 ? 100 : topPercent;
    }

    private static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }

    @Override
    public List<Coordinate> findPointsWithinRadius(SearchArea searchArea, SearchArea originSearchArea, GridSize gridSize) {
        log.debug("findPointsWithinRadius called with center={} radiusKm={} gridSize={}", searchArea.center(), searchArea.radiusKm(), gridSize);

        List<Coordinate> coordinates = new ArrayList<>();

        if (searchArea.radiusKm() <= 0 || searchArea.center() == null) {
            log.warn("findPointsWithinRadius: invalid parameters radiusKm={} center={}", searchArea.radiusKm(), searchArea.center());
            return coordinates;
        }

        double centerLat = searchArea.center().latitude();
        double centerLon = searchArea.center().longitude();

        double radiusInDegrees = searchArea.radiusKm() / KM_PER_DEGREE;

        double minLat = centerLat - radiusInDegrees;
        double maxLat = centerLat + radiusInDegrees;
        double minLon = centerLon - radiusInDegrees;
        double maxLon = centerLon + radiusInDegrees;

        for (double lat = minLat; lat <= maxLat; lat += gridSize.latitudeDegrees()) {
            for (double lon = minLon; lon <= maxLon; lon += gridSize.longitudeDegrees()) {
                double distance = distanceService.findDistance(new Coordinate(centerLat, centerLon), new Coordinate(lat, lon));
                double distanceFromOrigin = distanceService.findDistance(originSearchArea.center(), new Coordinate(lat, lon));
                if (distance <= searchArea.radiusKm() && distanceFromOrigin <= originSearchArea.radiusKm()) {
                    coordinates.add(new Coordinate(lat, lon));
                }
                int coordinatesSize = coordinates.size();
                if (log.isDebugEnabled() && coordinatesSize > 0 && coordinatesSize % 10 == 0) {
                    log.debug("Generated {} points within radius {} km from center {}", coordinatesSize, searchArea.radiusKm(), searchArea.center());
                }
            }
        }

        if (coordinates.size() > 1000) {
            log.warn("Large number of points generated ({}) - consider tuning gridSize or radius.", coordinates.size());
        }

        log.debug("Generated {} points within radius {} km from center {}", coordinates.size(), searchArea.radiusKm(), searchArea.center());
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
                .collect(Collectors.toList());

        return getTopLocationConditions(brightnessList);
    }

    @Override
    public List<LocationConditions> getTopLocationConditions(List<LocationConditions> list) {
        List<LocationConditions> sortedList = list.stream()
                .sorted(Comparator.comparingDouble(LocationConditions::brightness))
                .toList();
        int limit = (int) Math.ceil(list.size() * (topPercent / 100.0));

        int finalSize = Math.min(Math.max(limit, topNumber), list.size());
        log.debug("filterTopByBrightness() return {} of {} coordinates", finalSize, list.size());
        return sortedList.subList(0, finalSize);
    }

    @Override
    public List<LocationConditions> searchBestSpotsRecursive(SearchParams searchParams) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start("searchBestSpotsRecursive [depth={}]");
        log.debug("searchBestSpotsRecursive [depth={}]: Parameters radiusKm={} depth={} maxDepth={}", searchParams.depth(), searchParams.searchContext().searchArea().radiusKm(), searchParams.depth(), searchParams.searchContext().maxDepth());

        if (searchParams.depth() > searchParams.searchContext().maxDepth() || searchParams.searchContext().searchArea().radiusKm() < 0.035) {
            log.debug("searchBestSpotsRecursive [depth={}]: invalid parameters radiusKm={} depth={} maxDepth={}", searchParams.depth(), searchParams.searchContext().searchArea().radiusKm(), searchParams.depth(), searchParams.searchContext().maxDepth());
            return Collections.emptyList();
        }

        List<Coordinate> gridPoints = findPointsWithinRadius(searchParams.searchContext().searchArea(), searchParams.originSearchArea(), searchParams.gridSize());

        if (gridPoints.isEmpty()) {
            log.debug("searchBestSpotsRecursive [depth={}]: list gridPoints is empty, thickening the grid.", searchParams.depth());

            SearchContext searchContext = SearchContext.builder()
                    .maxDepth(searchParams.searchContext().maxDepth())
                    .gridDiv(searchParams.searchContext().gridDiv())
                    .searchArea(searchParams.searchContext().searchArea())
                    .build();
            GridSize nextGrid = GridSize.builder()
                    .latitudeDegrees(searchParams.gridSize().latitudeDegrees() / searchParams.searchContext().gridDiv())
                    .longitudeDegrees(searchParams.gridSize().longitudeDegrees() / searchParams.searchContext().gridDiv())
                    .build();
            SearchParams nextSearchParams = SearchParams.builder()
                    .searchContext(searchContext)
                    .gridSize(nextGrid)
                    .depth(searchParams.depth())
                    .originSearchArea(searchParams.originSearchArea())
                    .build();

            return searchBestSpotsRecursive(nextSearchParams);
        }
        log.debug("searchBestSpotsRecursive [depth={}]: list gridPoints has size {}", searchParams.depth(), gridPoints.size());

        List<LocationConditions> topSpots = filterTopByBrightness(gridPoints);

        if (topSpots.isEmpty()) {
            log.debug("searchBestSpotsRecursive [depth={}]: list topSpots is empty", searchParams.depth());
            return Collections.emptyList();
        }
        log.debug("searchBestSpotsRecursive [depth={}]: list topSpots has size {}", searchParams.depth(), topSpots.size());

        List<LocationConditions> result = new ArrayList<>(topSpots);

        List<CompletableFuture<List<LocationConditions>>> futures = topSpots.stream()
                .map(spot -> supplyAsync(() -> {
                    Coordinate subCenter = spot.coordinate();
                    double nextRadius = calculateNewRadius(searchParams.gridSize());
                    GridSize nextGrid = GridSize.builder()
                            .latitudeDegrees(searchParams.gridSize().latitudeDegrees() / searchParams.searchContext().gridDiv())
                            .longitudeDegrees(searchParams.gridSize().longitudeDegrees() / searchParams.searchContext().gridDiv())
                            .build();

                    SearchContext searchContext = SearchContext.builder()
                            .maxDepth(searchParams.searchContext().maxDepth())
                            .gridDiv(searchParams.searchContext().gridDiv())
                            .searchArea(SearchArea.builder()
                                    .center(subCenter)
                                    .radiusKm(nextRadius)
                                    .build())
                            .build();

                    SearchParams nextSearchParams = SearchParams.builder()
                            .searchContext(searchContext)
                            .gridSize(nextGrid)
                            .depth(searchParams.depth() + 1)
                            .originSearchArea(searchParams.originSearchArea())
                            .build();
                    return searchBestSpotsRecursive(nextSearchParams);
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
        log.info("searchBestSpotsRecursive finished at depth={} in {} ms", searchParams.depth(), stopWatch.getTotalTimeMillis());
        log.debug("searchBestSpotsRecursive finished at depth={} in {} ms, result size:{}", searchParams.depth(), stopWatch.getTotalTimeMillis(), result.size());

        return result;
    }

    private double calculateNewRadius(GridSize gridSize) {
        double kmPerDegreeLatitude = 111.0;
        double kmPerDegreeLongitude = 70.0;  // averaged for Poland (approx. 49-55°N)

        double sideNorthSouth = gridSize.latitudeDegrees() * kmPerDegreeLatitude;
        double sideEastWest = gridSize.longitudeDegrees() * kmPerDegreeLongitude;

        double newRadius = Math.max(sideNorthSouth, sideEastWest) * 1.5;
        log.debug("calculateNewRadius for {}: {}", gridSize, newRadius);
        return newRadius;
    }

    protected <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, executorService);
    }

    @PreDestroy
    public void shutdownExecutor() {
        executorService.shutdown();
    }
}
