package com.aldhafara.astroSpotFinder.service;

import com.aldhafara.astroSpotFinder.model.Coordinate;
import com.aldhafara.astroSpotFinder.model.WeatherForecastResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WeatherForecastServiceImplTest {

    private final String baseUrl = "http://mocked-weather-service";
    @Mock
    private RestTemplate restTemplate;
    @InjectMocks
    private WeatherForecastServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new WeatherForecastServiceImpl(restTemplate, baseUrl);
    }

    @Test
    void getNightForecast_returnsResponse() throws ExecutionException, InterruptedException {
        Coordinate coordinate = new Coordinate(52.23, 21.01);
        String timezone = "Europe/Warsaw";

        WeatherForecastResponse expectedResponse = mock(WeatherForecastResponse.class);

        when(restTemplate.getForObject(any(URI.class), eq(WeatherForecastResponse.class)))
                .thenReturn(expectedResponse);

        CompletableFuture<WeatherForecastResponse> future = service.getNightForecast(coordinate, timezone);
        WeatherForecastResponse actual = future.get();

        assertNotNull(actual);
        assertSame(expectedResponse, actual);

        verify(restTemplate, times(1)).getForObject(any(URI.class), eq(WeatherForecastResponse.class));
    }

    @Test
    void getNightForecast_usesDefaultTimezoneIfBlank() throws ExecutionException, InterruptedException {
        Coordinate coordinate = new Coordinate(52.23, 21.01);
        String timezone = " ";

        WeatherForecastResponse expectedResponse = mock(WeatherForecastResponse.class);

        when(restTemplate.getForObject(any(URI.class), eq(WeatherForecastResponse.class)))
                .thenReturn(expectedResponse);

        CompletableFuture<WeatherForecastResponse> future = service.getNightForecast(coordinate, timezone);
        WeatherForecastResponse actual = future.get();

        assertNotNull(actual);
        verify(restTemplate, times(1)).getForObject(argThat(uri -> uri.toString().contains("timezone=Europe/Warsaw")), eq(WeatherForecastResponse.class));
    }

    @Test
    void getNightForecast_handlesTooManyRequestsException() {
        Coordinate coordinate = new Coordinate(52.23, 21.01);
        String timezone = "Europe/Warsaw";

        when(restTemplate.getForObject(any(URI.class), eq(WeatherForecastResponse.class)))
                .thenThrow(HttpClientErrorException.TooManyRequests.create(HttpStatusCode.valueOf(429), null, null, null, null));

        CompletableFuture<WeatherForecastResponse> future = service.getNightForecast(coordinate, timezone);

        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(HttpClientErrorException.TooManyRequests.class, ex.getCause());

        verify(restTemplate, times(1)).getForObject(any(URI.class), eq(WeatherForecastResponse.class));
    }

    @Test
    void getNightForecast_handlesRestClientException() {
        Coordinate coordinate = new Coordinate(52.23, 21.01);
        String timezone = "Europe/Warsaw";

        when(restTemplate.getForObject(any(URI.class), eq(WeatherForecastResponse.class)))
                .thenThrow(new RestClientException("Generic error"));

        CompletableFuture<WeatherForecastResponse> future = service.getNightForecast(coordinate, timezone);

        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(RestClientException.class, ex.getCause());

        verify(restTemplate, times(1)).getForObject(any(URI.class), eq(WeatherForecastResponse.class));
    }
}
