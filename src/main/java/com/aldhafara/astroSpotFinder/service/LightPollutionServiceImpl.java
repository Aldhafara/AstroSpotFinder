package com.aldhafara.astroSpotFinder.service;

import com.aldhafara.astroSpotFinder.model.Coordinate;
import com.aldhafara.astroSpotFinder.model.LightPollutionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Optional;

@Service
@ConditionalOnProperty(prefix = "lightpollutionservice", name = "url")
public class LightPollutionServiceImpl implements LightPollutionService {

    private static final Logger log = LoggerFactory.getLogger(LightPollutionServiceImpl.class);

    private final RestTemplate restTemplate;
    private final String serviceUrl;

    public LightPollutionServiceImpl(RestTemplate restTemplate,
                                     @Value("${lightpollutionservice.url}") String serviceUrl) {
        log.debug("Using LightPollutionServiceImpl as LightPollutionService implementation");
        this.restTemplate = restTemplate;
        this.serviceUrl = serviceUrl;
    }

    @Override
    @Cacheable("lightPollution")
    public Optional<LightPollutionInfo> getLightPollution(Coordinate coordinate) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        URI uri = buildDarknessUrl(coordinate);
        try {
            LightPollutionInfo response = restTemplate.getForObject(
                    uri, LightPollutionInfo.class);

            stopWatch.stop();

            if (response == null) {
                log.warn("LightPollutionService returned null response for coordinate {} in {}ms",
                        coordinate, stopWatch.getTotalTimeMillis());
                return Optional.empty();
            }

            if (!isValidCoordinate(response.latitude(), response.longitude())) {
                log.warn("LightPollutionService returned invalid coordinates {} for request {}, time: {}ms",
                        response, coordinate, stopWatch.getTotalTimeMillis());
                return Optional.empty();
            }

            if (response.relativeBrightness() < 0.0 || response.relativeBrightness() > 255.0) {
                log.warn("LightPollutionService returned out-of-range relativeBrightness {} for coordinate {}, time: {}ms",
                        response.relativeBrightness(), coordinate, stopWatch.getTotalTimeMillis());
                return Optional.empty();
            }

            log.info("LightPollutionService successful response for {} in {}ms, relativeBrightness={}",
                    coordinate, stopWatch.getTotalTimeMillis(), response.relativeBrightness());

            return Optional.of(response);

        } catch (HttpClientErrorException.TooManyRequests e) {
            stopWatch.stop();
            log.warn("429 Too Many Requests for coordinate {}: will NOT cache this error", coordinate);
            throw e;
        } catch (RestClientException e) {
            stopWatch.stop();
            log.error("LightPollutionService request failed for coordinate {} (URL: {}) in {}ms",
                    coordinate, uri, stopWatch.getTotalTimeMillis(), e);
            return Optional.empty();
        }
    }

    private URI buildDarknessUrl(Coordinate coordinate) {
        return UriComponentsBuilder.fromUriString(serviceUrl + "/darkness")
                .queryParam("latitude", coordinate.latitude())
                .queryParam("longitude", coordinate.longitude())
                .build()
                .toUri();
    }

    private boolean isValidCoordinate(double lat, double lon) {
        return lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
    }
}
