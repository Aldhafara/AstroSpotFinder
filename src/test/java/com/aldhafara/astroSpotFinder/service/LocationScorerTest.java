package com.aldhafara.astroSpotFinder.service;

import com.aldhafara.astroSpotFinder.model.AggregatedWeatherData;
import com.aldhafara.astroSpotFinder.model.DataPeriod;
import com.aldhafara.astroSpotFinder.model.HourlyData;
import com.aldhafara.astroSpotFinder.model.HourlyUnits;
import com.aldhafara.astroSpotFinder.model.LocationConditions;
import com.aldhafara.astroSpotFinder.model.ScoringParameters;
import com.aldhafara.astroSpotFinder.model.ScoringWeights;
import com.aldhafara.astroSpotFinder.model.WeatherForecastResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocationScorerTest {

    private LocationScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = new LocationScorer();
    }

    @Test
    void testNormalize_basicCases() {
        assertThrows(IllegalArgumentException.class, () -> scorer.normalize(5, 10, 5));

        assertEquals(0.0, scorer.normalize(-1, 0, 10));

        assertEquals(1.0, scorer.normalize(20, 0, 10));

        assertEquals(0.5, scorer.normalize(5, 0, 10), 1e-6);
    }

    @Test
    void testIsHourInRange_normalAndWrapAround() throws Exception {
        var method = LocationScorer.class.getDeclaredMethod("isHourInRange", String.class, int.class, int.class);
        method.setAccessible(true);

        assertTrue((Boolean) method.invoke(scorer, "10:00", 8, 22));

        assertFalse((Boolean) method.invoke(scorer, "7:00", 8, 22));

        assertTrue((Boolean) method.invoke(scorer, "23:00", 22, 6));

        assertTrue((Boolean) method.invoke(scorer, "5:00", 22, 6));

        assertFalse((Boolean) method.invoke(scorer, "7:00", 22, 6));
    }

    @Test
    void testAggregateWeatherDataPerNight_emptyHoursFiltered() {
        HourlyData hour1 = new HourlyData(1755824400, "1:00", 10, 1000, 5, 7, 15);
        DataPeriod period = new DataPeriod("2025-08-22", 0.0, List.of(hour1));
        WeatherForecastResponse weather = weatherForecastResponse(List.of(period));

        LocationConditions location = new LocationConditions(null, 0, weather, null);

        var result = scorer.aggregateWeatherDataPerNight(location, 10, 12);
        assertTrue(result.isEmpty());
    }

    @Test
    void testAggregateWeatherDataPerNight_calculation() {
        HourlyData hour1 = new HourlyData(1755896400, "21:00", 14.5, 10, 10000, 2.5, 5.8);
        HourlyData hour2 = new HourlyData(1755900000, "22:00", 20.0, 50, 20000, 4.5, 5);
        DataPeriod period = new DataPeriod("2025-08-22", 0.0, List.of(hour1, hour2));
        WeatherForecastResponse weather = weatherForecastResponse(List.of(period));
        LocationConditions location = new LocationConditions(null, 0, weather, null);

        var aggregated = scorer.aggregateWeatherDataPerNight(location, 20, 23);

        assertEquals(1, aggregated.size());
        AggregatedWeatherData data = aggregated.get("2025-08-22");
        assertNotNull(data);
        assertEquals(17.25, data.avgTemperature());
        assertEquals(30.0, data.avgCloudCover(), 1e-6);
        assertEquals(15000.0, data.avgVisibility(), 1e-6);
        assertEquals(4.5, data.maxWindSpeed(), 1e-6);
        assertEquals(5.8, data.maxWindGust(), 1e-6);
    }

    @Test
    void testCalculateScore_weightsAppliedCorrectly() throws Exception {
        LocationConditions loc = new LocationConditions(null, 100, null, null);
        AggregatedWeatherData agg = new AggregatedWeatherData(loc, 50, 15000, 7.5, 10, 20);

        ScoringWeights weights = new ScoringWeights(1.0, 1.0, 1.0, 1.0, 1.0, 1.0);

        var method = LocationScorer.class.getDeclaredMethod("calculateScore", AggregatedWeatherData.class, ScoringWeights.class);
        method.setAccessible(true);

        double score = (double) method.invoke(scorer, agg, weights);

        // Calculate expected value manually:
        // lightPollutionScore = 1 - normalize(100, 0, 255) = 1 - 100/255 ≈ 0.607843
        // cloudCoverScore = 1 - 50/100 = 0.5
        // visibilityScore = normalize(15000, 0, 30000) = 0.5
        // windSpeedScore = 1 - 7.5/15 = 0.5
        // windGustScore = 1 - 10/20 = 0.5
        // total = sum = 0.607843 + 0.5 + 0.5 + 0.5 + 0.5 = 2.607843

        double expected = 0.607843 + 0.5 + 0.5 + 0.5 + 0.5;
        System.out.println(expected);
        System.out.println(score);
        assertEquals(expected, score, 1e-5);
    }

    @Test
    void testScoreLocation_defaultParameters_whenNull() {
        WeatherForecastResponse weather = weatherForecastResponse(List.of());
        LocationConditions location = new LocationConditions(null, 0, weather, null);

        Map<String, Double> scores = scorer.scoreLocation(location, null);

        assertTrue(scores.isEmpty());
    }

    @Test
    void testScoreLocation_withValidParameters() {
        HourlyData hour1 = new HourlyData(1755896400, "21:00", 10, 1000, 5, 7, 15);
        HourlyData hour2 = new HourlyData(1755900000, "22:00", 20, 2000, 6, 8, 14);
        DataPeriod period = new DataPeriod("2025-08-22", 0.0, List.of(hour1, hour2));
        WeatherForecastResponse weather = weatherForecastResponse(List.of(period));
        LocationConditions location = new LocationConditions(null, 100, weather, null);

        ScoringParameters params = ScoringParameters.defaultParameters();

        Map<String, Double> scores = scorer.scoreLocation(location, params);

        assertEquals(1, scores.size());
        assertTrue(scores.containsKey("2025-08-22"));
    }

    private WeatherForecastResponse weatherForecastResponse(List<DataPeriod> periods) {
        return new WeatherForecastResponse(
                52.232222,
                21.008333,
                0.130,
                7200,
                "Europe/Warsaw",
                "GMT+2",
                115,
                new HourlyUnits(
                        "iso8601",
                        "%",
                        "°C",
                        "m",
                        "m/s",
                        "m/s"),
                periods);
    }
}
