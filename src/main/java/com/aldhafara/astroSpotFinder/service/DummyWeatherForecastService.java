package com.aldhafara.astroSpotFinder.service;

import com.aldhafara.astroSpotFinder.model.Coordinate;
import com.aldhafara.astroSpotFinder.model.WeatherForecastResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@ConditionalOnProperty(prefix = "weatherforecastservice", name = "provider", havingValue = "dummy", matchIfMissing = false)
public class DummyWeatherForecastService implements WeatherForecastService {

    private static final Logger log = LoggerFactory.getLogger(DummyWeatherForecastService.class);

    @Override
    @Cacheable("weatherforecast.dummy")
    public CompletableFuture<WeatherForecastResponse> getNightForecast(Coordinate coordinate, String timezone) {
        log.info("Using DummyWeatherForecastService for coordinate {} and timezone {}", coordinate, timezone);
        var dummyResponse = new WeatherForecastResponse(/*TODO Implement WeatherForecastResponse*/);
        return CompletableFuture.completedFuture(dummyResponse);
    }
}
