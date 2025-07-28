package com.aldhafara.astroSpotFinder.service;

import com.aldhafara.astroSpotFinder.model.Coordinate;
import com.aldhafara.astroSpotFinder.model.GridSize;
import com.aldhafara.astroSpotFinder.model.LightPollutionInfo;
import com.aldhafara.astroSpotFinder.model.LocationConditions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class AstroSpotServiceImplTest {

    @Mock
    private LightPollutionService lightPollutionService;

    @Mock
    private StraightLineDistanceService distanceService;

    private AstroSpotServiceImpl astroSpotService;

    private static final double KM_PER_DEGREE = 111.0;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        astroSpotService = new AstroSpotServiceImpl(lightPollutionService, distanceService, 3, 50);
    }

    @Test
    void findPointsWithinRadius_returnsEmptyForNonPositiveRadius() {
        Coordinate center = new Coordinate(50, 20);
        GridSize gridSize = new GridSize(0.1, 0.1);

        List<Coordinate> resultZero = astroSpotService.findPointsWithinRadius(center, 0, gridSize);
        List<Coordinate> resultNegative = astroSpotService.findPointsWithinRadius(center, -10, gridSize);

        assertTrue(resultZero.isEmpty());
        assertTrue(resultNegative.isEmpty());
    }

    @Test
    void findPointsWithinRadius_returnsEmptyForNullCenter() {
        GridSize gridSize = new GridSize(0.1, 0.1);
        List<Coordinate> result = astroSpotService.findPointsWithinRadius(null, 100, gridSize);
        assertTrue(result.isEmpty());
    }

    @Test
    void findPointsWithinRadius_returnsPointsWithinRadius() {
        Coordinate center = new Coordinate(50, 20);
        GridSize gridSize = new GridSize(0.1, 0.1);
        double radiusKm = 20;

        when(distanceService.findDistance(eq(center), argThat(c ->
                Math.abs(c.latitude() - 50) < 0.15 && Math.abs(c.longitude() - 20) < 0.15)))
                .thenReturn(15.0);
        when(distanceService.findDistance(eq(center), argThat(c ->
                Math.abs(c.latitude() - 50) >= 0.15 || Math.abs(c.longitude() - 20) >= 0.15)))
                .thenReturn(25.0);

        List<Coordinate> results = astroSpotService.findPointsWithinRadius(center, radiusKm, gridSize);

        assertFalse(results.isEmpty());

        for (Coordinate coord : results) {
            double dist = distanceService.findDistance(center, coord);
            assertTrue(dist <= radiusKm);
        }
    }

    @Test
    void findPointsWithinRadius_handlesGridSizeNull_useDefaultGridSize() {
        astroSpotService = new AstroSpotServiceImpl(null, distanceService, 5, 50);
        Coordinate center = new Coordinate(0, 0);
        double radiusKm = 15;

        when(distanceService.findDistance(any(), any())).thenReturn(0.0);

        List<Coordinate> results = astroSpotService.findPointsWithinRadius(center, radiusKm, new GridSize(0.09, 0.15));

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

}
