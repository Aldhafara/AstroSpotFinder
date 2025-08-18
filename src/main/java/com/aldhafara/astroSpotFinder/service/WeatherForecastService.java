package com.aldhafara.astroSpotFinder.service;

import com.aldhafara.astroSpotFinder.model.WeatherForecastResponse;
import com.aldhafara.astroSpotFinder.model.Coordinate;

import java.util.concurrent.CompletableFuture;

public interface WeatherForecastService {
    CompletableFuture<WeatherForecastResponse> getNightForecast(Coordinate coordinate, String timezone);
}
