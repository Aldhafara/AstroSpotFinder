package com.aldhafara.astroSpotFinder.service;

import com.aldhafara.astroSpotFinder.model.Coordinate;
import com.aldhafara.astroSpotFinder.model.LightPollutionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class DummyLightPollutionService implements LightPollutionService {

    private static final Logger log = LoggerFactory.getLogger(DummyLightPollutionService.class);

    @Override
    @Cacheable("lightPollution_dummy")
    public Optional<LightPollutionInfo> getLightPollution(Coordinate coordinate) {
        log.info("Use DummyLightPollutionService receiving response for {}", coordinate);
        return Optional.of(new LightPollutionInfo(
                coordinate.latitude(),
                coordinate.longitude(),
                Math.random() * 255
        ));
    }
}
