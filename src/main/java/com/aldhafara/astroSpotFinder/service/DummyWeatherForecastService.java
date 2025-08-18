package com.aldhafara.astroSpotFinder.service;

import com.aldhafara.astroSpotFinder.model.Coordinate;
import com.aldhafara.astroSpotFinder.model.DataPeriod;
import com.aldhafara.astroSpotFinder.model.HourlyData;
import com.aldhafara.astroSpotFinder.model.HourlyUnits;
import com.aldhafara.astroSpotFinder.model.WeatherForecastResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@ConditionalOnProperty(prefix = "weatherforecastservice", name = "provider", havingValue = "dummy", matchIfMissing = false)
public class DummyWeatherForecastService implements WeatherForecastService {

    private static final Logger log = LoggerFactory.getLogger(DummyWeatherForecastService.class);

    @Override
    @Cacheable("weatherforecast.dummy")
    public CompletableFuture<WeatherForecastResponse> getNightForecast(Coordinate coordinate, String timezone) {
        log.info("Using DummyWeatherForecastService for coordinate {} and timezone {}", coordinate, timezone);
        var dummyResponse = new WeatherForecastResponse(
                52.232222,
                21.008333,
                0.1,
                7200,
                "Europe/Warsaw",
                "GMT+2",
                113,
                new HourlyUnits(
                        "iso8601",
                        "%",
                        "°C",
                        "m",
                        "m/s",
                        "m/s"
                ),
                List.of(
                        new DataPeriod(
                                "2025-08-21/2025-08-22",
                                0.41,
                                List.of(
                                        new HourlyData(
                                                1755810000L,
                                                "21:00",
                                                14.9,
                                                3,
                                                16720.0,
                                                4.7,
                                                8.9
                                        ),
                                        new HourlyData(
                                                1755813600L,
                                                "22:00",
                                                14.3,
                                                5,
                                                16000.0,
                                                4.5,
                                                8.2
                                        )
                                )
                        )
                )
        );
        return CompletableFuture.completedFuture(dummyResponse);
    }
}
