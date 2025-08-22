package com.aldhafara.astroSpotFinder.service;

import com.aldhafara.astroSpotFinder.model.Coordinate;
import com.aldhafara.astroSpotFinder.model.GridSize;
import com.aldhafara.astroSpotFinder.model.LocationConditions;
import com.aldhafara.astroSpotFinder.model.ScoringParameters;
import com.aldhafara.astroSpotFinder.model.SearchParams;
import com.aldhafara.astroSpotFinder.model.SearchArea;
import com.aldhafara.astroSpotFinder.model.SimplifiedLocationConditions;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface AstroSpotService {

    List<Coordinate> findPointsWithinRadius(SearchArea searchArea, SearchArea originSearchArea, GridSize gridSize);
    List<LocationConditions> filterTopByBrightness(List<Coordinate> points);
    List<LocationConditions> searchBestSpotsRecursive(SearchParams searchParams);
    List<LocationConditions> getTopLocationConditions(List<LocationConditions> list);
    CompletableFuture<Map<String, List<SimplifiedLocationConditions>>> getBestSpotsWithWeatherScoring(List<LocationConditions> preliminaryLocations, ScoringParameters parameters, String timezone);
}
