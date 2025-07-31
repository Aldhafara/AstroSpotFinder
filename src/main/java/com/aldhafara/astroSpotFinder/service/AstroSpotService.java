package com.aldhafara.astroSpotFinder.service;

import com.aldhafara.astroSpotFinder.model.Coordinate;
import com.aldhafara.astroSpotFinder.model.GridSize;
import com.aldhafara.astroSpotFinder.model.LocationConditions;
import com.aldhafara.astroSpotFinder.model.SearchParams;
import com.aldhafara.astroSpotFinder.model.SearchArea;

import java.util.List;

public interface AstroSpotService {

    List<Coordinate> findPointsWithinRadius(SearchArea searchArea, SearchArea originSearchArea, GridSize gridSize);
    List<LocationConditions> filterTopByBrightness(List<Coordinate> points);
    List<LocationConditions> searchBestSpotsRecursive(SearchParams searchParams);
    List<LocationConditions> getTopLocationConditions(List<LocationConditions> list);
}
