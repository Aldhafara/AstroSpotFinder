package com.aldhafara.astroSpotFinder.service;

import com.aldhafara.astroSpotFinder.model.Coordinate;
import com.aldhafara.astroSpotFinder.model.GridSize;
import com.aldhafara.astroSpotFinder.model.LightPollutionInfo;
import com.aldhafara.astroSpotFinder.model.LocationConditions;
import com.aldhafara.astroSpotFinder.model.SearchArea;
import com.aldhafara.astroSpotFinder.model.SearchContext;
import com.aldhafara.astroSpotFinder.model.SearchParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AstroSpotServiceImplTest {

    private static final double KM_PER_DEGREE = 111.0;
    @Mock
    private LightPollutionService lightPollutionService;
    @Mock
    private StraightLineDistanceService distanceService;
    private AstroSpotServiceImpl astroSpotService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        astroSpotService = new AstroSpotServiceImpl(lightPollutionService, distanceService, 3, 50);
    }

    @Test
    void findPointsWithinRadius_returnsEmptyForNonPositiveRadius() {
        List<Coordinate> resultZero = astroSpotService.findPointsWithinRadius(SearchArea.builder().center(center).radiusKm(0).build(), originSearchArea, gridSize);
        List<Coordinate> resultNegative = astroSpotService.findPointsWithinRadius(SearchArea.builder().center(center).radiusKm(-10).build(), originSearchArea, gridSize);

        assertTrue(resultZero.isEmpty());
        assertTrue(resultNegative.isEmpty());
    }

    @Test
    void findPointsWithinRadius_returnsEmptyForNullCenter() {
        List<Coordinate> result = astroSpotService.findPointsWithinRadius(SearchArea.builder().center(null).radiusKm(100).build(), originSearchArea, gridSize);
        assertTrue(result.isEmpty());
    }

    @Test
    void findPointsWithinRadius_returnsPointsWithinRadius() {
        Coordinate localCenter = new Coordinate(70, 30);
        double radiusKm = 20;
        SearchArea searchArea = SearchArea.builder().center(localCenter).radiusKm(radiusKm).build();

        SearchArea originSearchArea = SearchArea.builder().center(center).radiusKm(1000).build();

        when(distanceService.findDistance(eq(localCenter), argThat(c ->
                Math.abs(c.latitude() - localCenter.latitude()) < 0.15 &&
                        Math.abs(c.longitude() - localCenter.longitude()) < 0.15)))
                .thenReturn(15.0);

        when(distanceService.findDistance(eq(localCenter), argThat(c ->
                Math.abs(c.latitude() - localCenter.latitude()) >= 0.15 ||
                        Math.abs(c.longitude() - localCenter.longitude()) >= 0.15)))
                .thenReturn(25.0);

        when(distanceService.findDistance(eq(center), any())).thenReturn(10.0);

        List<Coordinate> results = astroSpotService.findPointsWithinRadius(searchArea, originSearchArea, gridSize);

        assertFalse(results.isEmpty(), "The list of points should not be empty");

        for (Coordinate coord : results) {
            double distFromCenter = distanceService.findDistance(localCenter, coord);
            assertTrue(distFromCenter <= radiusKm,
                    String.format("Location %s is too far from center: %.2f km, limit %f km", coord, distFromCenter, radiusKm));

            double distFromOrigin = distanceService.findDistance(center, coord);
            assertTrue(distFromOrigin <= originSearchArea.radiusKm(),
                    String.format("Location %s is too far from originCenter: %.2f km, limit %f km", coord, distFromOrigin, originSearchArea.radiusKm()));
        }
    }

    @Test
    void findPointsWithinRadius_handlesGridSizeNull_useDefaultGridSize() {
        astroSpotService = new AstroSpotServiceImpl(null, distanceService, 5, 50);
        Coordinate center = new Coordinate(0, 0);
        double radiusKm = 15;

        when(distanceService.findDistance(any(), any())).thenReturn(0.0);

        List<Coordinate> results = astroSpotService.findPointsWithinRadius(SearchArea.builder().center(center).radiusKm(radiusKm).build(),originSearchArea, new GridSize(0.09, 0.15));

        assertFalse(results.isEmpty());

        Coordinate first = results.getFirst();
        assertTrue(first.latitude() >= center.latitude() - (radiusKm / KM_PER_DEGREE));
        assertTrue(first.longitude() >= center.longitude() - (radiusKm / KM_PER_DEGREE));
    }

    @Test
    void filterTopByBrightness_shouldReturnEmpty_whenInputIsNull() {
        List<LocationConditions> result = astroSpotService.filterTopByBrightness(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void filterTopByBrightness_shouldReturnEmpty_whenInputIsEmpty() {
        List<LocationConditions> result = astroSpotService.filterTopByBrightness(Collections.emptyList());
        assertTrue(result.isEmpty());
    }

    @Test
    void filterTopByBrightness_shouldReturnEmptyAndLogWarn_whenTopPercentBelowOrEqualZero() {
        astroSpotService = new AstroSpotServiceImpl(lightPollutionService, null, 3, 0);
        List<LocationConditions> result = astroSpotService.filterTopByBrightness(List.of(new Coordinate(10, 20)));
        assertTrue(result.isEmpty());
    }

    @Test
    void filterTopByBrightness_shouldReturnAtLeastTopNumberResults_evenIfPercentIsLow() {
        List<Coordinate> coords = Arrays.asList(
                new Coordinate(10, 10),
                new Coordinate(20, 20),
                new Coordinate(30, 30),
                new Coordinate(40, 40)
        );

        when(lightPollutionService.getLightPollution(coords.get(0))).thenReturn(Optional.of(new LightPollutionInfo(10, 10, 0.9)));
        when(lightPollutionService.getLightPollution(coords.get(1))).thenReturn(Optional.of(new LightPollutionInfo(20, 20, 0.2)));
        when(lightPollutionService.getLightPollution(coords.get(2))).thenReturn(Optional.of(new LightPollutionInfo(30, 30, 0.5)));
        when(lightPollutionService.getLightPollution(coords.get(3))).thenReturn(Optional.of(new LightPollutionInfo(40, 40, 0.7)));

        astroSpotService = new AstroSpotServiceImpl(lightPollutionService, null, 3, 10);

        List<LocationConditions> result = astroSpotService.filterTopByBrightness(coords);

        assertEquals(3, result.size());

        assertTrue(result.get(0).brightness() <= result.get(1).brightness());
        assertTrue(result.get(1).brightness() <= result.get(2).brightness());
    }

    @Test
    void filterTopByBrightness_shouldReturnCorrectTopPercent() {
        List<Coordinate> coords = Arrays.asList(
                new Coordinate(0, 0),
                new Coordinate(1, 1),
                new Coordinate(2, 2),
                new Coordinate(3, 3),
                new Coordinate(4, 4),
                new Coordinate(5, 5)
        );

        for (int i = 0; i < coords.size(); i++) {
            when(lightPollutionService.getLightPollution(coords.get(i)))
                    .thenReturn(Optional.of(new LightPollutionInfo(coords.get(i).latitude(), coords.get(i).longitude(), 0.1 + 0.1 * i)));
        }

        astroSpotService = new AstroSpotServiceImpl(lightPollutionService, null, 1, 50);

        List<LocationConditions> result = astroSpotService.filterTopByBrightness(coords);
        assertEquals(3, result.size());

        for (int i = 1; i < result.size(); i++) {
            assertTrue(result.get(i).brightness() >= result.get(i - 1).brightness());
        }
    }

    @Test
    void filterTopByBrightness_shouldSkipPointsWithNoBrightnessData() {
        List<Coordinate> coords = Arrays.asList(
                new Coordinate(0, 0),
                new Coordinate(1, 1),
                new Coordinate(2, 2)
        );

        when(lightPollutionService.getLightPollution(coords.get(0))).thenReturn(Optional.empty());
        when(lightPollutionService.getLightPollution(coords.get(1))).thenReturn(Optional.of(new LightPollutionInfo(1, 1, 0.1)));
        when(lightPollutionService.getLightPollution(coords.get(2))).thenReturn(Optional.empty());

        astroSpotService = new AstroSpotServiceImpl(lightPollutionService, null, 1, 100);

        List<LocationConditions> result = astroSpotService.filterTopByBrightness(coords);
        assertEquals(1, result.size());
        assertEquals(coords.get(1), result.get(0).coordinate());
    }

    @Test
    void testSearchBestSpotsRecursive_BaseCases_returnEmpty() {
        Coordinate center = new Coordinate(0, 0);
        GridSize gridSize = new GridSize(1.0, 1.0);
        SearchArea originSearchArea = SearchArea.builder().center(center).radiusKm(10).build();

        SearchArea searchArea1 = SearchArea.builder().center(center).radiusKm(10).build();
        SearchContext searchContext1 = SearchContext.builder().searchArea(searchArea1).maxDepth(4).gridDiv(2).build();
        SearchParams searchParams1 = SearchParams.builder().searchContext(searchContext1).gridSize(gridSize).depth(5).originSearchArea(originSearchArea).build();
        List<LocationConditions> result1 = astroSpotService.searchBestSpotsRecursive(searchParams1);
        assertTrue(result1.isEmpty());

        SearchArea searchArea2 = SearchArea.builder().center(center).radiusKm(0.03).build();
        SearchContext searchContext2 = SearchContext.builder().searchArea(searchArea2).maxDepth(4).gridDiv(2).build();
        SearchParams searchParams2 = SearchParams.builder().searchContext(searchContext2).gridSize(gridSize).depth(0).originSearchArea(originSearchArea).build();
        List<LocationConditions> result2 = astroSpotService.searchBestSpotsRecursive(searchParams2);
        assertTrue(result2.isEmpty());
    }

    @Test
    void testSearchBestSpotsRecursive_emptyGridPoints_thenNonEmptyGridPoints_returnsResult() {
        AstroSpotServiceImpl spyService = spy(astroSpotService);

        Coordinate center = new Coordinate(0, 0);
        GridSize initialGridSize = new GridSize(1.0, 1.0);
        GridSize smallerGridSize = new GridSize(0.5, 0.5);

        doAnswer(invocation -> {
            SearchArea searchArea = invocation.getArgument(0);
            GridSize gridSize = invocation.getArgument(2);
            if (Math.abs(gridSize.latitudeDegrees() - initialGridSize.latitudeDegrees()) < 0.0001 &&
                    Math.abs(gridSize.longitudeDegrees() - initialGridSize.longitudeDegrees()) < 0.0001) {
                return Collections.emptyList();
            } else {
                return List.of(new Coordinate(1, 1));
            }
        }).when(spyService).findPointsWithinRadius(any(), any(), any());

        doReturn(List.of(new LocationConditions(new Coordinate(1, 1), 0.5))).when(spyService).filterTopByBrightness(anyList());

        SearchArea originSearchArea = SearchArea.builder()
                .center(center)
                .radiusKm(10)
                .build();

        SearchContext searchContext = SearchContext.builder()
                .maxDepth(4)
                .gridDiv(2)
                .searchArea(SearchArea.builder().center(center).radiusKm(10).build())
                .build();

        SearchParams params = SearchParams.builder()
                .searchContext(searchContext)
                .gridSize(initialGridSize)
                .depth(0)
                .originSearchArea(originSearchArea)
                .build();

        List<LocationConditions> result = spyService.searchBestSpotsRecursive(params);

        assertFalse(result.isEmpty(), "The result should not be empty after grid compaction");
        assertTrue(result.stream().anyMatch(loc -> loc.coordinate().equals(new Coordinate(1, 1))));
    }

    @Test
    void testSearchBestSpotsRecursive_emptyTopSpots_returnEmpty() {
        AstroSpotServiceImpl spyService = spy(astroSpotService);

        List<Coordinate> coords = List.of(new Coordinate(1, 1), new Coordinate(2, 2));
        doReturn(coords).when(spyService).findPointsWithinRadius(any(), any(), any());
        doReturn(Collections.emptyList()).when(spyService).filterTopByBrightness(coords);

        SearchArea searchArea = SearchArea.builder().center(new Coordinate(0, 0)).radiusKm(10).build();
        SearchContext searchContext = SearchContext.builder().maxDepth(4).gridDiv(2).searchArea(searchArea).build();
        SearchParams searchParams = SearchParams.builder().searchContext(searchContext).gridSize(GridSize.builder().latitudeDegrees(1).longitudeDegrees(1).build()).depth(0).originSearchArea(originSearchArea).build();

        List<LocationConditions> result = spyService.searchBestSpotsRecursive(searchParams);
        assertTrue(result.isEmpty());
    }

    @Test
    void testSearchBestSpotsRecursive_fullFlow_returnsAggregatedResults() {
        AstroSpotServiceImpl spyService = spy(astroSpotService);

        Coordinate spot1 = new Coordinate(1, 1);
        LocationConditions locCond1 = new LocationConditions(spot1, 10);
        List<LocationConditions> topSpots = List.of(locCond1);
        List<Coordinate> gridPoints = List.of(spot1);

        doReturn(gridPoints).when(spyService).findPointsWithinRadius(any(), any(), any());
        doReturn(topSpots).when(spyService).filterTopByBrightness(gridPoints);

        doAnswer(invocation -> CompletableFuture.completedFuture(Collections.emptyList()))
                .when(spyService).supplyAsync(any());

        SearchArea searchArea = SearchArea.builder().center(new Coordinate(0, 0)).radiusKm(10).build();
        SearchContext searchContext = SearchContext.builder().maxDepth(4).gridDiv(2).searchArea(searchArea).build();
        SearchParams searchParams = SearchParams.builder().searchContext(searchContext).gridSize(GridSize.builder().latitudeDegrees(1).longitudeDegrees(1).build()).depth(0).originSearchArea(originSearchArea).build();

        List<LocationConditions> results = spyService.searchBestSpotsRecursive(searchParams);

        assertFalse(results.isEmpty());
        assertTrue(results.contains(locCond1));
    }

    @Test
    void testIsInvalidSearchParams_depthExceedsMax() {
        SearchParams params = new SearchParams(
                new SearchContext(2, 2, new SearchArea(new Coordinate(0, 0), 1)),
                new GridSize(1.0, 1.0),
                3,
                new SearchArea(new Coordinate(0,0), 1)
        );
        assertTrue(astroSpotService.isInvalidSearchParams(params));
    }

    @Test
    void testIsInvalidSearchParams_radiusTooSmall() {
        SearchParams params = new SearchParams(
                new SearchContext(5, 2, new SearchArea(new Coordinate(0, 0), 0.03)),
                new GridSize(1.0, 1.0),
                1,
                new SearchArea(new Coordinate(0,0), 1)
        );
        assertTrue(astroSpotService.isInvalidSearchParams(params));
    }

    @Test
    void testIsInvalidSearchParams_validParams() {
        SearchParams params = new SearchParams(
                new SearchContext(5, 2, new SearchArea(new Coordinate(0, 0), 1)),
                new GridSize(1.0, 1.0),
                1,
                new SearchArea(new Coordinate(0,0), 1)
        );
        assertFalse(astroSpotService.isInvalidSearchParams(params));
    }

    @Test
    void testRecursiveForEmptyGrid_callsSearchAgainWithSmallerGrid() {
        AstroSpotServiceImpl spyService = spy(astroSpotService);
        SearchArea originSearchArea = new SearchArea(new Coordinate(0,0), 10);
        SearchArea searchArea = new SearchArea(new Coordinate(0,0), 10);
        GridSize initialGrid = new GridSize(1.0, 1.0);
        int depth = 0;

        SearchContext context = new SearchContext(5, 2, searchArea);
        SearchParams params = new SearchParams(context, initialGrid, depth, originSearchArea);

        GridSize expectedSmallerGrid = new GridSize(0.5, 0.5);

        doReturn(List.of(new LocationConditions(new Coordinate(1,2), 10.0)))
                .when(spyService)
                .searchBestSpotsRecursive(argThat(searchParams ->
                        Double.compare(searchParams.gridSize().latitudeDegrees(), expectedSmallerGrid.latitudeDegrees()) == 0 &&
                                Double.compare(searchParams.gridSize().longitudeDegrees(), expectedSmallerGrid.longitudeDegrees()) == 0 &&
                                searchParams.depth() == depth
                ));
        List<LocationConditions> result = spyService.recursiveForEmptyGrid(params);

        assertNotNull(result, "Result should not be null");
        assertEquals(1, result.size(), "A mock list with one location should be returned");
        verify(spyService, times(1)).searchBestSpotsRecursive(any(SearchParams.class));
    }

    @Test
    void testRecursiveSearchForSpotsReturnsAggregatedResults() {
        AstroSpotServiceImpl spyService = spy(astroSpotService);

        SearchParams baseParams = new SearchParams(
                new SearchContext(3, 2, new SearchArea(new Coordinate(0, 0), 10)),
                new GridSize(1.0, 1.0),
                0,
                new SearchArea(new Coordinate(0,0), 10)
        );

        LocationConditions spot1 = new LocationConditions(new Coordinate(0.1, 0.1), 10);
        LocationConditions spot2 = new LocationConditions(new Coordinate(0.2, 0.2), 20);

        List<LocationConditions> topSpots = List.of(spot1, spot2);

        doReturn(List.of(spot1))
                .when(spyService).searchBestSpotsRecursive(any(SearchParams.class));


        List<LocationConditions> results = spyService.recursiveSearchForTopSpots(baseParams, topSpots);

        assertNotNull(results);
        assertFalse(results.isEmpty(), "Result should not be empty");
        assertTrue(results.contains(spot1), "Result should contain spot1");
    }

    Coordinate center = new Coordinate(50, 20);
    GridSize gridSize = new GridSize(0.1, 0.1);
    SearchArea originSearchArea = SearchArea.builder().center(center).radiusKm(1).build();
}
