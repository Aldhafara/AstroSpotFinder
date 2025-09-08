package com.aldhafara.astroSpotFinder.service;

import com.aldhafara.astroSpotFinder.configuration.TopLocationsConfig;
import com.aldhafara.astroSpotFinder.model.Coordinate;
import com.aldhafara.astroSpotFinder.model.GridSize;
import com.aldhafara.astroSpotFinder.model.LocationConditions;
import com.aldhafara.astroSpotFinder.model.LocationsCluster;
import com.aldhafara.astroSpotFinder.model.ScoringParameters;
import com.aldhafara.astroSpotFinder.model.SearchArea;
import com.aldhafara.astroSpotFinder.model.SearchContext;
import com.aldhafara.astroSpotFinder.model.SearchParams;
import com.aldhafara.astroSpotFinder.model.SimplifiedLocationConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;
import org.springframework.web.client.HttpClientErrorException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class AstroSpotServiceImpl implements AstroSpotService {

    private static final Logger log = LoggerFactory.getLogger(AstroSpotServiceImpl.class);

    private static final double KM_PER_DEGREE = 65.4;

    private final LightPollutionService lightPollutionService;
    private final DistanceService distanceService;
    private final StraightLineDistanceService straightLineDistanceService;
    private final ExecutorService executorService;
    private final WeatherForecastService weatherForecastService;
    private final LocationScorer locationScorer;
    private final int topNumber;
    private final double topPercent;
    private final boolean filterWithTies;

    public AstroSpotServiceImpl(LightPollutionService lightPollutionService,
                                DistanceService distanceService,
                                StraightLineDistanceService straightLineDistanceService,
                                WeatherForecastService weatherForecastService,
                                LocationScorer locationScorer,
                                TopLocationsConfig topLocationsConfig) {
        int processors = Runtime.getRuntime().availableProcessors();
        log.info("Number of available processors: {}", processors);

        this.lightPollutionService = lightPollutionService;
        this.distanceService = distanceService;
        this.straightLineDistanceService = straightLineDistanceService;
        this.weatherForecastService = weatherForecastService;
        this.locationScorer = locationScorer;
        this.executorService = new ThreadPoolExecutor(
                0, processors * 2,
                15L, TimeUnit.SECONDS,
                new SynchronousQueue<>());
        this.topNumber = topLocationsConfig.number() <= 0 ? 1 : topLocationsConfig.number();
        this.topPercent = topLocationsConfig.percent() > 100 ? 100 : topLocationsConfig.percent();
        this.filterWithTies = topLocationsConfig.extended();
    }

    @Override
    public List<LocationsCluster> searchBestLocationsClusters(SearchParams searchParams) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start("searchBestLocationsClusters [depth=%d]".formatted(searchParams.depth()));
        log.debug("searchBestLocationsClusters [depth={}]: Parameters radiusKm={} depth={} maxDepth={}",
                searchParams.depth(), searchParams.searchContext().searchArea().radiusKm(),
                searchParams.depth(), searchParams.searchContext().maxDepth());

        if (isInvalidSearchParams(searchParams)) {
            log.debug("searchBestLocationsClusters [depth={}]: invalid parameters radiusKm={} depth={} maxDepth={}",
                    searchParams.depth(), searchParams.searchContext().searchArea().radiusKm(),
                    searchParams.depth(), searchParams.searchContext().maxDepth());
            return Collections.emptyList();
        }

        Set<Coordinate> gridPoints = findPointsWithinRadius(
                searchParams.searchContext().searchArea(),
                searchParams.originSearchArea(),
                searchParams.gridSize());

        if (gridPoints.isEmpty()) {
            log.debug("searchBestLocationsClusters [depth={}]: list gridPoints is empty, thickening the grid.",
                    searchParams.depth());
            return Collections.emptyList();
        }
        log.debug("searchBestLocationsClusters [depth={}]: list gridPoints has size {}", searchParams.depth(), gridPoints.size());

        Set<LocationConditions> locationsWithBrightness = getLocationsWithBrightness(gridPoints);
        Set<LocationConditions> brightestSpots = getTopLocationConditions(locationsWithBrightness);

        if (brightestSpots.isEmpty()) {
            log.debug("searchBestLocationsClusters [depth={}]: list brightestSpots is empty", searchParams.depth());
            return Collections.emptyList();
        }
        log.debug("searchBestLocationsClusters [depth={}]: list brightestSpots has size {}", searchParams.depth(), brightestSpots.size());

        Coordinate center = searchParams.searchContext().searchArea().center();
        double latDelta = searchParams.gridSize().latitudeDegrees();
        double lonDelta = searchParams.gridSize().longitudeDegrees();

        Coordinate pointB = new Coordinate(center.latitude() + latDelta, center.longitude() + lonDelta);

        double epsDistance = straightLineDistanceService.findDistance(center, pointB);
        epsDistance *= 1.1;

        List<LocationsCluster> clusters = clusterByProximity(brightestSpots, epsDistance);

        stopWatch.stop();
        log.info("searchBestLocationsClusters finished at depth={} in {} ms, clusters size:{}", searchParams.depth(), stopWatch.getTotalTimeMillis(), clusters.size());

        return recursiveSearchForClusters(searchParams, clusters);
    }

    private List<LocationsCluster> recursiveSearchForClusters(SearchParams searchParams, List<LocationsCluster> clusters) {
        List<CompletableFuture<LocationsCluster>> futures = clusters.stream()
                .map(cluster -> CompletableFuture.supplyAsync(() -> {
                    Set<LocationConditions> updatedSet =
                            recursiveSearchForTopSpotsInCluster(searchParams, cluster.getLocations());

                    Set<LocationConditions> filteredSet = updatedSet.stream().filter(Objects::nonNull)
                            .collect(Collectors.toSet());

                    if (filteredSet.isEmpty()) {
                        return null;
                    }
                    return new LocationsCluster(filteredSet);
                }))
                .toList();

        List<LocationsCluster> updatedClusters = futures.stream()
                .map(future -> {
                    try {
                        return future.join();
                    } catch (CompletionException e) {
                        log.error("Exception during async cluster recursive search", e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();

        return updatedClusters;
    }

    private Set<LocationConditions> recursiveSearchForTopSpotsInCluster(SearchParams searchParams, Set<LocationConditions> currentClusterPoints) {

        if (searchParams.depth() >= searchParams.searchContext().maxDepth()) {
            return currentClusterPoints;
        }

        List<LocationConditions> clusterList = new ArrayList<>(currentClusterPoints);

        List<CompletableFuture<Set<LocationConditions>>> futures = clusterList.stream()
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

        Set<LocationConditions> aggregatedResults = futures.stream()
                .map(future -> {
                    try {
                        return future.join();
                    } catch (CompletionException e) {
                        log.error("Async recursive search error", e);
                        return Collections.<LocationConditions>emptyList();
                    }
                })
                .filter(list -> !list.isEmpty())
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());

        Set<Coordinate> aggregatedResultsCoordinates = aggregatedResults.stream().map(LocationConditions::coordinate).collect(Collectors.toSet());
        Set<LocationConditions> locationsWithBrightness = getLocationsWithBrightness(aggregatedResultsCoordinates);
        Set<LocationConditions> currentClusterPointsWithNewLocationsWithBrightness = new HashSet<>(currentClusterPoints);
        currentClusterPointsWithNewLocationsWithBrightness.addAll(locationsWithBrightness);
        Set<LocationConditions> brightestSpots = getTopLocationConditions(currentClusterPointsWithNewLocationsWithBrightness);

        currentClusterPoints.clear();
        currentClusterPoints.addAll(brightestSpots);

        if (searchParams.depth() + 1 <= searchParams.searchContext().maxDepth()) {
            SearchParams nextParams = SearchParams.builder()
                    .searchContext(searchParams.searchContext())
                    .gridSize(getNextGrid(searchParams))
                    .depth(searchParams.depth() + 1)
                    .originSearchArea(searchParams.originSearchArea())
                    .build();

            return recursiveSearchForTopSpotsInCluster(nextParams, currentClusterPoints);
        } else {
            return currentClusterPoints;
        }
    }

    public Set<LocationConditions> searchBestSpotsRecursive(SearchParams searchParams) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start("searchBestSpotsRecursive [depth=%d]".formatted(searchParams.depth()));
        log.debug("searchBestSpotsRecursive [depth={}]: Parameters radiusKm={} depth={} maxDepth={}", searchParams.depth(), searchParams.searchContext().searchArea().radiusKm(), searchParams.depth(), searchParams.searchContext().maxDepth());

        if (isInvalidSearchParams(searchParams)) {
            log.debug("searchBestSpotsRecursive [depth={}]: invalid parameters radiusKm={} depth={} maxDepth={}", searchParams.depth(), searchParams.searchContext().searchArea().radiusKm(), searchParams.depth(), searchParams.searchContext().maxDepth());
            return Collections.emptySet();
        }

        Set<Coordinate> gridPoints = findPointsWithinRadius(searchParams.searchContext().searchArea(), searchParams.originSearchArea(), searchParams.gridSize());

        if (gridPoints.isEmpty()) {
            log.debug("searchBestSpotsRecursive [depth={}]: list gridPoints is empty, thickening the grid.", searchParams.depth());
            return recursiveForEmptyGrid(searchParams);
        }
        log.debug("searchBestSpotsRecursive [depth={}]: list gridPoints has size {}", searchParams.depth(), gridPoints.size());

        Set<LocationConditions> locationsWithBrightness = getLocationsWithBrightness(gridPoints);
        Set<LocationConditions> brightestSpots = getTopLocationConditions(locationsWithBrightness);

        if (brightestSpots.isEmpty()) {
            log.debug("searchBestSpotsRecursive [depth={}]: list brightestSpots is empty", searchParams.depth());
            return Collections.emptySet();
        }
        log.debug("searchBestSpotsRecursive [depth={}]: list brightestSpots has size {}", searchParams.depth(), brightestSpots.size());

        Set<LocationConditions> subResults = recursiveSearchForTopSpotsInCluster(searchParams, brightestSpots);
        Set<LocationConditions> combined = new HashSet<>(brightestSpots);
        combined.addAll(subResults);

        Set<LocationConditions> result = getTopLocationConditions(combined);

        stopWatch.stop();
        log.info("searchBestSpotsRecursive finished at depth={} in {} ms", searchParams.depth(), stopWatch.getTotalTimeMillis());
        log.debug("searchBestSpotsRecursive finished at depth={} in {} ms, result size:{}", searchParams.depth(), stopWatch.getTotalTimeMillis(), result.size());

        return result;
    }

    Set<LocationConditions> recursiveForEmptyGrid(SearchParams searchParams) {
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

    Set<LocationConditions> getLocationsWithBrightness(Set<Coordinate> coordinates) {
        if (coordinates == null || coordinates.isEmpty()) {
            log.info("There is no coordinates to check.");
            return Collections.emptySet();
        }
        if (topPercent <= 0) {
            log.warn("The percentage of coordinates examined is set below 0. Top {} coordinates will be taken into account.", topNumber);
            return Collections.emptySet();
        }
        if (topPercent > 50) {
            log.warn("The percentage of coordinates examined is set to high ({}%). This may negatively impact performance.", topPercent);
        }

        return coordinates.parallelStream()
                .distinct()
                .map(coord -> {
                    try {
                        return lightPollutionService.getLightPollution(coord)
                                .map(info -> new LocationConditions(coord, info.relativeBrightness(), null, null))
                                .orElse(null);
                    } catch (HttpClientErrorException.TooManyRequests e) {
                        log.warn("Skipping coordinate {} due to 429 Too Many Requests", coord);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public Set<LocationConditions> getTopLocationConditions(Set<LocationConditions> locations) {
        if (locations.isEmpty()) {
            return Collections.emptySet();
        }

        List<LocationConditions> sortedList = locations.stream()
                .sorted(Comparator.comparingDouble(LocationConditions::brightness))
                .toList();

        int limit = (int) Math.ceil(sortedList.size() * (topPercent / 100.0));
        int finalSize = Math.min(Math.max(limit, topNumber), sortedList.size());

        if (finalSize == 0) {
            return Collections.emptySet();
        }

        if (filterWithTies) {
            double thresholdBrightness = sortedList.get(finalSize - 1).brightness();

            List<LocationConditions> extendedList = sortedList.stream()
                    .takeWhile(loc -> loc.brightness() <= thresholdBrightness)
                    .toList();

            log.debug("getTopLocationConditions() return {} of {} coordinates (with ties)", extendedList.size(), locations.size());

            return new LinkedHashSet<>(extendedList);
        } else {
            log.debug("getTopLocationConditions() return {} of {} coordinates", finalSize, locations.size());
            return new LinkedHashSet<>(sortedList.subList(0, finalSize));
        }
    }

    boolean isInvalidSearchParams(SearchParams searchParams) {
        return searchParams.depth() > searchParams.searchContext().maxDepth()
                || searchParams.searchContext().searchArea().radiusKm() < 0.035;
    }

    public Set<Coordinate> findPointsWithinRadius(SearchArea searchArea, SearchArea originSearchArea, GridSize gridSize) {
        log.debug("findPointsWithinRadius called with center={} radiusKm={} gridSize={}", searchArea.center(), searchArea.radiusKm(), gridSize);

        Set<Coordinate> coordinates = new HashSet<>();

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

        double gridLatDeg = gridSize.latitudeDegrees();
        double gridLonDeg = gridSize.longitudeDegrees();

        double minLatGrid = alignToGridLower(minLat, gridLatDeg);
        double maxLatGrid = alignToGridUpper(maxLat, gridLatDeg);
        double minLonGrid = alignToGridLower(minLon, gridLonDeg);
        double maxLonGrid = alignToGridUpper(maxLon, gridLonDeg);

        for (double lat = minLatGrid; lat <= maxLatGrid; lat += gridLatDeg) {
            for (double lon = minLonGrid; lon <= maxLonGrid; lon += gridLonDeg) {
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

    private double alignToGridLower(double value, double gridStep) {
        return Math.floor(value / gridStep) * gridStep;
    }

    private double alignToGridUpper(double value, double gridStep) {
        return Math.ceil(value / gridStep) * gridStep;
    }

    List<LocationsCluster> clusterByProximity(Set<LocationConditions> points, double eps) {
        List<LocationsCluster> clusters = new ArrayList<>();

        for (LocationConditions point : points) {
            LocationsCluster clusterToJoin = null;
            for (LocationsCluster cluster : clusters) {
                boolean close = cluster.getLocations().stream()
                        .anyMatch(loc -> straightLineDistanceService.findDistance(loc.coordinate(), point.coordinate()) <= eps);
                if (close) {
                    clusterToJoin = cluster;
                    break;
                }
            }
            if (clusterToJoin != null) {
                clusterToJoin.add(point);
            } else {
                clusters.add(new LocationsCluster(Set.of(point)));
            }
        }

        return mergeOverlappingClusters(clusters, eps);
    }

    List<LocationsCluster> mergeOverlappingClusters(List<LocationsCluster> clusters, double eps) {
        boolean merged;

        do {
            merged = false;
            outerLoop:
            for (int i = 0; i < clusters.size(); i++) {
                LocationsCluster clusterA = clusters.get(i);
                for (int j = i + 1; j < clusters.size(); j++) {
                    LocationsCluster clusterB = clusters.get(j);
                    if (clustersAreClose(clusterA, clusterB, eps)) {
                        for (LocationConditions loc : clusterB.getLocations()) {
                            clusterA.add(loc);
                        }
                        clusters.remove(j);
                        merged = true;
                        break outerLoop;
                    }
                }
            }
        } while (merged);

        return clusters;
    }

    private boolean clustersAreClose(LocationsCluster a, LocationsCluster b, double eps) {
        for (LocationConditions locA : a.getLocations()) {
            for (LocationConditions locB : b.getLocations()) {
                if (straightLineDistanceService.findDistance(locA.coordinate(), locB.coordinate()) <= eps) {
                    return true;
                }
            }
        }
        return false;
    }

    private GridSize getNextGrid(SearchParams searchParams) {
        return GridSize.builder()
                .latitudeDegrees(searchParams.gridSize().latitudeDegrees() / searchParams.searchContext().gridDiv())
                .longitudeDegrees(searchParams.gridSize().longitudeDegrees() / searchParams.searchContext().gridDiv())
                .build();
    }

    @Override
    public CompletableFuture<Map<String, List<SimplifiedLocationConditions>>> getBestSpotsWithWeatherScoringClusters(
            List<LocationsCluster> preliminaryLocationClusters,
            ScoringParameters parameters,
            String timezone) {

        List<CompletableFuture<List<LocationConditions>>> futures = preliminaryLocationClusters.stream()
                .map(cluster -> {
                    LocationConditions bestLocation = cluster.getLocations().stream()
                            .min(Comparator.comparingDouble(LocationConditions::brightness))
                            .orElse(null);

                    if (bestLocation == null) {
                        return CompletableFuture.completedFuture(Collections.<LocationConditions>emptyList());
                    }

                    return weatherForecastService.getNightForecast(bestLocation.coordinate(), timezone)
                            .thenApply(weather -> {
                                return cluster.getLocations().stream()
                                        .map(loc -> new LocationConditions(
                                                loc.coordinate(),
                                                loc.brightness(),
                                                weather,
                                                loc.score()))
                                        .toList();
                            });
                })
                .toList();

        CompletableFuture<Void> allDone = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

        return allDone.thenApply(v -> {
            List<LocationConditions> allLocationsWithWeather = futures.stream()
                    .map(CompletableFuture::join)
                    .flatMap(List::stream)
                    .toList();

            return locationScorer.scoreAndSortLocations(allLocationsWithWeather, parameters);
        });
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
}
