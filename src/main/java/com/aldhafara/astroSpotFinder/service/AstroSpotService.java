package com.aldhafara.astroSpotFinder.service;

import com.aldhafara.astroSpotFinder.model.LocationsCluster;
import com.aldhafara.astroSpotFinder.model.ScoringParameters;
import com.aldhafara.astroSpotFinder.model.SearchParams;
import com.aldhafara.astroSpotFinder.model.SimplifiedLocationConditions;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface AstroSpotService {

    List<LocationsCluster> searchBestLocationsClusters(SearchParams searchParams);

    CompletableFuture<Map<String, List<SimplifiedLocationConditions>>> getBestSpotsWithWeatherScoringClusters(
            List<LocationsCluster> preliminaryLocationClusters,
            ScoringParameters parameters,
            String timezone);
}
