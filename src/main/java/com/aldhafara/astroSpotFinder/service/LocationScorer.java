package com.aldhafara.astroSpotFinder.service;

import com.aldhafara.astroSpotFinder.model.AggregatedWeatherData;
import com.aldhafara.astroSpotFinder.model.DataPeriod;
import com.aldhafara.astroSpotFinder.model.HourlyData;
import com.aldhafara.astroSpotFinder.model.LocationConditions;
import com.aldhafara.astroSpotFinder.model.ScoringParameters;
import com.aldhafara.astroSpotFinder.model.ScoringWeights;
import com.aldhafara.astroSpotFinder.model.SimplifiedLocationConditions;
import com.aldhafara.astroSpotFinder.model.WeatherForecastResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Collections.emptyMap;

@Service
public class LocationScorer {
    private static final Logger log = LoggerFactory.getLogger(LocationScorer.class);
    private final double maxWindSpeedThreshold = 15.0;
    private final double maxWindGustThreshold = 20.0;
    private final double maxVisibility = 30_000.0;
    private final double maxCloudCover = 100;
    private final double maxBrightnessValue = 255;

    public Map<String, Double> scoreLocation(LocationConditions location, ScoringParameters parameters) {

        if (parameters == null) {
            parameters = ScoringParameters.defaultParameters();
            log.info("ScoringParameters are not defined, using default values: {}", parameters);
        } else {
            log.info("ScoringParameters are defined, using values: {}", parameters);
        }

        Map<String, AggregatedWeatherData> aggregatedWeather = aggregateWeatherDataPerNight(location, parameters.hourFrom(), parameters.hourTo());

        ScoringWeights weights = parameters.weights();
        if (aggregatedWeather == null || aggregatedWeather.isEmpty()) {
            log.info("AggregatedWeatherData is not defined or empty");
            return emptyMap();
        }
        return scoreAllAggregatedData(aggregatedWeather, weights);
    }

    private Map<String, Double> scoreAllAggregatedData(Map<String, AggregatedWeatherData> aggregatedWeather, ScoringWeights weights) {
        return aggregatedWeather.entrySet().parallelStream()
                .collect(Collectors.toConcurrentMap(
                        Map.Entry::getKey,
                        entry -> calculateScore(entry.getValue(), weights)
                ));
    }

    private double calculateScore(AggregatedWeatherData aggregatedWeather, ScoringWeights weights) {
        double lightPollutionScore = 1 - normalize(aggregatedWeather.locationConditions().brightness(), 0, maxBrightnessValue);
        double cloudCoverScore = 1 - aggregatedWeather.avgCloudCover() / maxCloudCover;
        double visibilityScore = normalize(aggregatedWeather.avgVisibility(), 0, maxVisibility);
        double windSpeedScore = 1 - aggregatedWeather.maxWindSpeed() / maxWindSpeedThreshold;
        double windGustScore = 1 - aggregatedWeather.maxWindGust() / maxWindGustThreshold;

        return weights.wLightPollution() * lightPollutionScore +
                weights.wCloudCover() * cloudCoverScore +
                weights.wVisibility() * visibilityScore +
                weights.wWindSpeed() * windSpeedScore +
                weights.wWindGust() * windGustScore;
    }

    double normalize(double value, double min, double max) {
        if (max <= min) {
            log.error("max must be greater than min, max:{}, min:{}", max, min);
            throw new IllegalArgumentException("max must be greater than min");
        }
        if (value <= min) {
            return 0.0;
        }
        if (value >= max) {
            return 1.0;
        }
        return (value - min) / (max - min);
    }

    Map<String, AggregatedWeatherData> aggregateWeatherDataPerNight(LocationConditions location, int hourFrom, int hourTo) {
        WeatherForecastResponse weather = location.weather();
        return weather.data().stream()
                .map(period -> {
                    List<HourlyData> filteredHours = period.hours().stream()
                            .filter(hourly -> isHourInRange(hourly.hour(), hourFrom, hourTo))
                            .toList();

                    if (filteredHours.isEmpty()) {
                        log.warn("filteredHours isEmpty");
                        return Optional.<Map.Entry<String, AggregatedWeatherData>>empty();
                    }

                    double avgCloudCover = filteredHours.stream()
                            .mapToInt(HourlyData::cloudCover)
                            .average()
                            .orElse(0);

                    double avgVisibility = filteredHours.stream()
                            .mapToDouble(HourlyData::visibility)
                            .average()
                            .orElse(0);

                    double maxWindSpeed = filteredHours.stream()
                            .mapToDouble(HourlyData::windSpeed)
                            .max()
                            .orElse(0);

                    double maxWindGust = filteredHours.stream()
                            .mapToDouble(HourlyData::windGust)
                            .max()
                            .orElse(0);

                    double avgTemperature = filteredHours.stream()
                            .mapToDouble(HourlyData::temperature)
                            .average()
                            .orElse(0);

                    AggregatedWeatherData aggregated = new AggregatedWeatherData(location, avgCloudCover, avgVisibility, maxWindSpeed, maxWindGust, avgTemperature);
                    return Optional.of(Map.entry(period.period(), aggregated));
                })
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    }

    private boolean isHourInRange(String hourStr, int from, int to) {
        int hour = parseHour(hourStr);
        if (from <= to) {
            return hour >= from && hour < to;
        } else {
            return hour >= from || hour < to;
        }
    }

    private int parseHour(String hourStr) {
        try {
            String[] parts = hourStr.split(":");
            return Integer.parseInt(parts[0]);
        } catch (Exception e) {
            log.error("Invalid hour format: {}", hourStr);
            throw new IllegalArgumentException("Invalid hour format: " + hourStr);
        }
    }

    public Map<String, List<SimplifiedLocationConditions>> scoreAndSortLocations(List<LocationConditions> preliminarySpots, ScoringParameters parameters) {
        List<LocationConditions> scoredSpots = preliminarySpots.parallelStream()
                .map(spot -> withScores(spot, parameters)).toList();
        Map<String, List<SimplifiedLocationConditions>> groupedByPeriod = groupByPeriod(scoredSpots);
        return sortLocationsByPeriodScore(groupedByPeriod);
    }

    private LocationConditions withScores(LocationConditions location, ScoringParameters parameters) {
        Map<String, Double> scores = scoreLocation(location, parameters);
        return mergeScoreIntoLocation(location, scores);
    }

    private Map<String, List<SimplifiedLocationConditions>> groupByPeriod(List<LocationConditions> locations) {
        Map<String, List<SimplifiedLocationConditions>> grouped = new HashMap<>();
        for (LocationConditions loc : locations) {
            for (String period : loc.score().keySet()) {
                grouped.computeIfAbsent(period, k -> new ArrayList<>()).add(
                        new SimplifiedLocationConditions(
                                loc.coordinate(),
                                loc.brightness(),
                                loc.weather().hourlyUnits(),
                                findByPeriod(loc.weather().data(), period),
                                loc.score().get(period)
                        )
                );
            }
        }
        return grouped;
    }

    private Optional<DataPeriod> findByPeriod(List<DataPeriod> data, String period) {
        return data.stream()
                .filter(dp -> dp.period().equals(period))
                .findFirst();
    }

    private Map<String, List<SimplifiedLocationConditions>> sortLocationsByPeriodScore(Map<String, List<SimplifiedLocationConditions>> groupedByPeriod) {
        groupedByPeriod.replaceAll((period, locations) ->
                locations.stream()
                        .sorted(Comparator.comparing(SimplifiedLocationConditions::score).reversed())
                        .toList()
        );
        return groupedByPeriod;
    }

    private LocationConditions mergeScoreIntoLocation(LocationConditions location, Map<String, Double> scoresMap) {
        return new LocationConditions(location.coordinate(),
                location.brightness(),
                location.weather(),
                scoresMap);
    }
}
