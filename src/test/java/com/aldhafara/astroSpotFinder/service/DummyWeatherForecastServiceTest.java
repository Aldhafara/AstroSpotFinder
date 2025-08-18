package com.aldhafara.astroSpotFinder.service;

import com.aldhafara.astroSpotFinder.model.Coordinate;
import com.aldhafara.astroSpotFinder.model.WeatherForecastResponse;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DummyWeatherForecastServiceTest {

    private final DummyWeatherForecastService service = new DummyWeatherForecastService();

    @Test
    void getNightForecast_returnsExpectedDummyResponse() throws ExecutionException, InterruptedException {
        var coordinate = new Coordinate(52.232222, 21.008333);
        String timezone = "Europe/Warsaw";
        WeatherForecastResponse response = service.getNightForecast(coordinate, timezone).get();

        assertNotNull(response);
        assertEquals(52.232222, response.latitude(), 0.00001);
        assertEquals(21.008333, response.longitude(), 0.00001);
        assertEquals("Europe/Warsaw", response.timezone());
        assertNotNull(response.hourlyUnits());
        assertNotNull(response.data());
        assertFalse(response.data().isEmpty());
        assertEquals(1, response.data().size());
        assertFalse(response.data().getFirst().hours().isEmpty());
        assertEquals(0.41, response.data().getFirst().moonIllumination());
        assertEquals("2025-08-21/2025-08-22", response.data().getFirst().period());
    }
}
