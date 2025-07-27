package com.aldhafara.astroSpotFinder.service;

import com.aldhafara.astroSpotFinder.model.Coordinate;
import com.aldhafara.astroSpotFinder.model.LightPollutionInfo;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DummyLightPollutionServiceTest {

    private final DummyLightPollutionService dummyService = new DummyLightPollutionService();

    @Test
    void shouldReturnOptionalWithLightPollutionInfo() {
        Coordinate coord = new Coordinate(15.0, 30.0);

        Optional<LightPollutionInfo> result = dummyService.getLightPollution(coord);

        assertTrue(result.isPresent());

        LightPollutionInfo info = result.get();
        assertEquals(coord.latitude(), info.latitude());
        assertEquals(coord.longitude(), info.longitude());

        assertTrue(info.relativeBrightness() >= 0.0);
        assertTrue(info.relativeBrightness() < 255.0);
    }
}