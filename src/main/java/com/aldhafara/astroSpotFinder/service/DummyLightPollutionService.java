package com.aldhafara.astroSpotFinder.service;

import com.aldhafara.astroSpotFinder.model.Coordinate;
import com.aldhafara.astroSpotFinder.model.LightPollutionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@ConditionalOnProperty(prefix = "lightpollutionservice", name = "provider", havingValue="dummy", matchIfMissing = false)
public class DummyLightPollutionService implements LightPollutionService {

    private static final Logger log = LoggerFactory.getLogger(DummyLightPollutionService.class);

    public DummyLightPollutionService() {
        log.debug("Using DummyLightPollutionService as LightPollutionService implementation");
    }

    @Override
    @Cacheable("lightPollution.dummy")
    public Optional<LightPollutionInfo> getLightPollution(Coordinate coordinate) {
        double brightness = Math.random() * 255;
        log.info("Use DummyLightPollutionService receiving response for {} : {}", coordinate, brightness);
        return Optional.of(new LightPollutionInfo(
                coordinate.latitude(),
                coordinate.longitude(),
                brightness
        ));
    }
}
