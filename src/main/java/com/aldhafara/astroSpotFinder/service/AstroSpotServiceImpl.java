package com.aldhafara.astroSpotFinder.service;

import com.aldhafara.astroSpotFinder.configuration.ExecutorConfig;
import com.aldhafara.astroSpotFinder.configuration.TopLocationsConfig;
import com.aldhafara.astroSpotFinder.model.Coordinate;
import com.aldhafara.astroSpotFinder.model.GridSize;
import com.aldhafara.astroSpotFinder.model.LocationConditions;
import com.aldhafara.astroSpotFinder.model.ScoringParameters;
import com.aldhafara.astroSpotFinder.model.SearchArea;
import com.aldhafara.astroSpotFinder.model.SearchContext;
import com.aldhafara.astroSpotFinder.model.SearchParams;
import com.aldhafara.astroSpotFinder.model.SimplifiedLocationConditions;
import com.aldhafara.astroSpotFinder.model.WeatherForecastResponse;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
    private final WeatherForecastService weatherForecastService;
    private final LocationScorer locationScorer;
    private final int topNumber;
    private final double topPercent;
    private final int maxConcurrentThreads;

    public AstroSpotServiceImpl(LightPollutionService lightPollutionService,
                                DistanceService distanceService,
                                WeatherForecastService weatherForecastService,
                                LocationScorer locationScorer,
                                TopLocationsConfig topLocationsConfig,
                                ExecutorConfig executorConfig) {
        this.lightPollutionService = lightPollutionService;
        this.distanceService = distanceService;
        this.weatherForecastService = weatherForecastService;
        this.locationScorer = locationScorer;
        this.executorService = Executors.newCachedThreadPool();
        this.topNumber = topLocationsConfig.number() <= 0 ? 1 : topLocationsConfig.number();
        this.topPercent = topLocationsConfig.percent() > 100 ? 100 : topLocationsConfig.percent();
        this.maxConcurrentThreads = executorConfig.threads();
    }

    private static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }

    private static GridSize getNextGrid(SearchParams searchParams) {
        return GridSize.builder()
                .latitudeDegrees(searchParams.gridSize().latitudeDegrees() / searchParams.searchContext().gridDiv())
                .longitudeDegrees(searchParams.gridSize().longitudeDegrees() / searchParams.searchContext().gridDiv())
                .build();
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
                        .map(info -> new LocationConditions(coord, info.relativeBrightness(), null, null))
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
    public CompletableFuture<Map<String, List<SimplifiedLocationConditions>>> getBestSpotsWithWeatherScoring(List<LocationConditions> preliminaryLocations, ScoringParameters parameters, String timezone) {
        List<CompletableFuture<LocationConditions>> futures = preliminaryLocations.stream()
                .map(location -> weatherForecastService.getNightForecast(location.coordinate(), timezone)
                        .thenApply(weather -> mergeWeatherIntoLocation(location, weather))
                )
                .toList();

        CompletableFuture<Void> allDone = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

        return allDone.thenApply(v -> {
            List<LocationConditions> locationsWithWeather = futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());

            return locationScorer.scoreAndSortLocations(locationsWithWeather, parameters);
        });
    }

    private LocationConditions mergeWeatherIntoLocation(LocationConditions location, WeatherForecastResponse weather) {
        return new LocationConditions(location.coordinate(),
                location.brightness(),
                weather,
                location.score());
    }

    @Override
    public List<LocationConditions> searchBestSpotsRecursive(SearchParams searchParams) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start("searchBestSpotsRecursive [depth={}]");
        log.debug("searchBestSpotsRecursive [depth={}]: Parameters radiusKm={} depth={} maxDepth={}", searchParams.depth(), searchParams.searchContext().searchArea().radiusKm(), searchParams.depth(), searchParams.searchContext().maxDepth());

        if (isInvalidSearchParams(searchParams)) {
            log.debug("searchBestSpotsRecursive [depth={}]: invalid parameters radiusKm={} depth={} maxDepth={}", searchParams.depth(), searchParams.searchContext().searchArea().radiusKm(), searchParams.depth(), searchParams.searchContext().maxDepth());
            return Collections.emptyList();
        }

        List<Coordinate> gridPoints = findPointsWithinRadius(searchParams.searchContext().searchArea(), searchParams.originSearchArea(), searchParams.gridSize());

        if (gridPoints.isEmpty()) {
            log.debug("searchBestSpotsRecursive [depth={}]: list gridPoints is empty, thickening the grid.", searchParams.depth());
            return recursiveForEmptyGrid(searchParams);
        }
        log.debug("searchBestSpotsRecursive [depth={}]: list gridPoints has size {}", searchParams.depth(), gridPoints.size());

        List<LocationConditions> topSpots = filterTopByBrightness(gridPoints);

        if (topSpots.isEmpty()) {
            log.debug("searchBestSpotsRecursive [depth={}]: list topSpots is empty", searchParams.depth());
            return Collections.emptyList();
        }
        log.debug("searchBestSpotsRecursive [depth={}]: list topSpots has size {}", searchParams.depth(), topSpots.size());

        List<LocationConditions> result = new ArrayList<>(topSpots);

        List<LocationConditions> subResults = recursiveSearchForTopSpots(searchParams, topSpots);

        result.addAll(subResults.stream()
                .filter(distinctByKey(loc -> List.of(loc.coordinate(), loc.brightness())))
                .toList());

        stopWatch.stop();
        log.info("searchBestSpotsRecursive finished at depth={} in {} ms", searchParams.depth(), stopWatch.getTotalTimeMillis());
        log.debug("searchBestSpotsRecursive finished at depth={} in {} ms, result size:{}", searchParams.depth(), stopWatch.getTotalTimeMillis(), result.size());

        return result;
    }

    boolean isInvalidSearchParams(SearchParams searchParams) {
        return searchParams.depth() > searchParams.searchContext().maxDepth()
                || searchParams.searchContext().searchArea().radiusKm() < 0.035;
    }

    List<LocationConditions> recursiveSearchForTopSpots(SearchParams searchParams, List<LocationConditions> topSpots) {

        List<LocationConditions> aggregatedResults = new ArrayList<>();

        List<List<LocationConditions>> partitions = new ArrayList<>();
        for (int i = 0; i < topSpots.size(); i += maxConcurrentThreads) {
            int end = Math.min(i + maxConcurrentThreads, topSpots.size());
            partitions.add(topSpots.subList(i, end));
        }

        for (List<LocationConditions> partition : partitions) {
            List<CompletableFuture<List<LocationConditions>>> futures = partition.stream()
                    .map(spot -> supplyAsync(() -> {
                        Coordinate subCenter = spot.coordinate();
                        double nextRadius = calculateNewRadius(searchParams.gridSize());
                        GridSize nextGrid = getNextGrid(searchParams);

                        SearchContext nextContext = SearchContext.builder()
                                .maxDepth(searchParams.searchContext().maxDepth())
                                .gridDiv(searchParams.searchContext().gridDiv())
                                .searchArea(new SearchArea(subCenter, nextRadius))
                                .build();

                        SearchParams nextParams = SearchParams.builder()
                                .searchContext(nextContext)
                                .gridSize(nextGrid)
                                .depth(searchParams.depth() + 1)
                                .originSearchArea(searchParams.originSearchArea())
                                .build();

                        return searchBestSpotsRecursive(nextParams);
                    }))
                    .toList();

            List<LocationConditions> partialResults = futures.stream()
                    .map(future -> {
                        try {
                            return future.join();
                        } catch (CompletionException ex) {
                            log.error("Exception in async recursive task", ex);
                            return Collections.<LocationConditions>emptyList();
                        }
                    })
                    .flatMap(List::stream)
                    .toList();

            aggregatedResults.addAll(partialResults);
        }

        return aggregatedResults;
    }

    List<LocationConditions> recursiveForEmptyGrid(SearchParams searchParams) {
        SearchContext searchContext = SearchContext.builder()
                .maxDepth(searchParams.searchContext().maxDepth())
                .gridDiv(searchParams.searchContext().gridDiv())
                .searchArea(searchParams.searchContext().searchArea())
                .build();
        GridSize nextGrid = getNextGrid(searchParams);
        SearchParams nextSearchParams = SearchParams.builder()
                .searchContext(searchContext)
                .gridSize(nextGrid)
                .depth(searchParams.depth())
                .originSearchArea(searchParams.originSearchArea())
                .build();

        return searchBestSpotsRecursive(nextSearchParams);
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
