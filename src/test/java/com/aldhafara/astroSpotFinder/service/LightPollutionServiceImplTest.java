package com.aldhafara.astroSpotFinder.service;

import com.aldhafara.astroSpotFinder.model.Coordinate;
import com.aldhafara.astroSpotFinder.model.LightPollutionInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class LightPollutionServiceImplTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private LightPollutionServiceImpl service;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        service = new LightPollutionServiceImpl(restTemplate, "http://dummy-url");
    }

    @Test
    void shouldReturnEmptyOptional_whenRestReturnsNull() {
        Coordinate coord = new Coordinate(10, 20);
        when(restTemplate.getForObject(any(String.class), eq(LightPollutionInfo.class))).thenReturn(null);

        Optional<LightPollutionInfo> result = service.getLightPollution(coord);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyOptional_whenCoordinatesAreInvalid() {
        Coordinate coord = new Coordinate(10, 20);
        LightPollutionInfo invalidResponse = new LightPollutionInfo(1000, 20, 10);
        when(restTemplate.getForObject(any(String.class), eq(LightPollutionInfo.class))).thenReturn(invalidResponse);

        Optional<LightPollutionInfo> result = service.getLightPollution(coord);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyOptional_whenRelativeBrightnessIsOutOfRange() {
        Coordinate coord = new Coordinate(10, 20);
        LightPollutionInfo invalidResponse = new LightPollutionInfo(10, 20, -1);
        when(restTemplate.getForObject(any(String.class), eq(LightPollutionInfo.class))).thenReturn(invalidResponse);

        Optional<LightPollutionInfo> result = service.getLightPollution(coord);

        assertTrue(result.isEmpty());

        LightPollutionInfo invalidResponse2 = new LightPollutionInfo(10, 20, 300);
        when(restTemplate.getForObject(any(String.class), eq(LightPollutionInfo.class))).thenReturn(invalidResponse2);

        Optional<LightPollutionInfo> result2 = service.getLightPollution(coord);

        assertTrue(result2.isEmpty());
    }

    @Test
    void shouldReturnOptionalWithValue_whenResponseIsValid() {
        Coordinate coord = new Coordinate(10, 20);
        LightPollutionInfo validResponse = new LightPollutionInfo(10, 20, 100);
        when(restTemplate.getForObject(any(), eq(LightPollutionInfo.class))).thenReturn(validResponse);

        Optional<LightPollutionInfo> result = service.getLightPollution(coord);

        assertTrue(result.isPresent());
        assertEquals(validResponse, result.get());
    }

    @Test
    void shouldReturnEmptyOptional_whenRestClientExceptionThrown() {
        Coordinate coord = new Coordinate(10, 20);
        when(restTemplate.getForObject(any(String.class), eq(LightPollutionInfo.class)))
                .thenThrow(new RestClientException("Service error"));

        Optional<LightPollutionInfo> result = service.getLightPollution(coord);

        assertTrue(result.isEmpty());
    }
}