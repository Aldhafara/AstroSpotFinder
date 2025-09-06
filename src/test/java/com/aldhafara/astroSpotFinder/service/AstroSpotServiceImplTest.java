package com.aldhafara.astroSpotFinder.service;

import com.aldhafara.astroSpotFinder.configuration.TopLocationsConfig;
import com.aldhafara.astroSpotFinder.model.Coordinate;
import com.aldhafara.astroSpotFinder.model.GridSize;
import com.aldhafara.astroSpotFinder.model.LightPollutionInfo;
import com.aldhafara.astroSpotFinder.model.LocationConditions;
import com.aldhafara.astroSpotFinder.model.LocationsCluster;
import com.aldhafara.astroSpotFinder.model.SearchArea;
import com.aldhafara.astroSpotFinder.model.SearchContext;
import com.aldhafara.astroSpotFinder.model.SearchParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AstroSpotServiceImplTest {

    @Mock
    LightPollutionService lightPollutionService;
    @Mock
    DistanceService distanceService;
    @Mock
    StraightLineDistanceService straightLineDistanceService;
    @Mock
    WeatherForecastService weatherForecastService;
    @Mock
    LocationScorer locationScorer;
    TopLocationsConfig topLocationsConfig;

    AstroSpotServiceImpl service;

    @BeforeEach
    void setup() {
        topLocationsConfig = mock(TopLocationsConfig.class);
        when(topLocationsConfig.number()).thenReturn(3);
        when(topLocationsConfig.percent()).thenReturn(10.0);
        when(topLocationsConfig.extended()).thenReturn(false);

        service = new AstroSpotServiceImpl(
                lightPollutionService,
                distanceService,
                straightLineDistanceService,
                weatherForecastService,
                locationScorer,
                topLocationsConfig
        );
    }

    @Test
    void findPointsWithinRadius_returnsExpectedCoordinates() {
        Coordinate center = new Coordinate(50, 20);
        SearchArea searchArea = new SearchArea(center, 2.0);
        SearchArea originArea = searchArea;
        GridSize gridSize = new GridSize(0.01, 0.01);

        when(distanceService.findDistance(any(), any())).thenReturn(1.0);

        Set<Coordinate> result = service.findPointsWithinRadius(searchArea, originArea, gridSize);

        assertFalse(result.isEmpty());
        assertEquals(72, result.size());
    }

    @Test
    void getLocationsWithBrightness_mapsBrightnessCorrectly() {
        Coordinate coord1 = new Coordinate(50, 21);
        LightPollutionInfo info = new LightPollutionInfo(50, 21,0.2);

        when(lightPollutionService.getLightPollution(coord1)).thenReturn(Optional.of(info));

        Set<LocationConditions> result = service.getLocationsWithBrightness(Set.of(coord1));

        assertEquals(1, result.size());
        assertEquals(0.2, result.iterator().next().brightness());
    }

    @Test
    void getTopLocationConditions_returnsCorrectTopResults() {
        LocationConditions loc1 = new LocationConditions(new Coordinate(1, 1), 0.1, null, null);
        LocationConditions loc2 = new LocationConditions(new Coordinate(1, 2), 0.2, null, null);
        LocationConditions loc3 = new LocationConditions(new Coordinate(2, 1), 0.3, null, null);
        LocationConditions loc4 = new LocationConditions(new Coordinate(2, 2), 0.4, null, null);
        LocationConditions loc5 = new LocationConditions(new Coordinate(2, 3), 0.5, null, null);

        Set<LocationConditions> input = Set.of(loc1, loc2, loc3, loc4, loc5);

        Set<LocationConditions> result = service.getTopLocationConditions(input);

        assertEquals(3, result.size());
        assertTrue(result.contains(loc1));
    }

    @Test
    void isInvalidSearchParams_detectsInvalidParams() {
        SearchContext ctx = SearchContext.builder()
                .searchArea(new SearchArea(new Coordinate(0, 0), 0.01))
                .maxDepth(1)
                .gridDiv(2)
                .build();

        SearchParams params = SearchParams.builder()
                .searchContext(ctx)
                .gridSize(new GridSize(0.1, 0.1))
                .depth(2)
                .originSearchArea(new SearchArea(new Coordinate(0, 0), 10))
                .build();

        assertTrue(service.isInvalidSearchParams(params));
    }

    @Test
    void mergeOverlappingClusters_mergesCloseClustersCorrectly() {
        double eps = 10.0;

        LocationConditions loc1 = new LocationConditions(new Coordinate(50.0, 20.0), 0.1, null, null);
        LocationConditions loc2 = new LocationConditions(new Coordinate(50.001, 20.001), 0.2, null, null);
        LocationConditions loc3 = new LocationConditions(new Coordinate(50.1, 20.1), 0.3, null, null);

        LocationsCluster cluster1 = new LocationsCluster(Set.of(loc1));
        LocationsCluster cluster2 = new LocationsCluster(Set.of(loc2));
        LocationsCluster cluster3 = new LocationsCluster(Set.of(loc3));

        List<LocationsCluster> clusters = new ArrayList<>(List.of(cluster1, cluster2, cluster3));

        when(straightLineDistanceService.findDistance(loc1.coordinate(), loc2.coordinate())).thenReturn(5.0);

        when(straightLineDistanceService.findDistance(loc1.coordinate(), loc3.coordinate())).thenReturn(20.0);

        when(straightLineDistanceService.findDistance(loc2.coordinate(), loc3.coordinate())).thenReturn(20.0);

        List<LocationsCluster> mergedClusters = service.mergeOverlappingClusters(clusters, eps);

        assertEquals(2, mergedClusters.size());

        boolean mergedContainsLoc1AndLoc2 = mergedClusters.stream()
                .anyMatch(c -> c.getLocations().contains(loc1) && c.getLocations().contains(loc2));
        assertTrue(mergedContainsLoc1AndLoc2);

        boolean clusterContainsLoc3 = mergedClusters.stream()
                .anyMatch(c -> c.getLocations().contains(loc3));
        assertTrue(clusterContainsLoc3);
    }


}
