package com.aldhafara.astroSpotFinder.service;

import com.aldhafara.astroSpotFinder.model.Coordinate;
import com.aldhafara.astroSpotFinder.model.WeatherForecastResponse;
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
import java.util.concurrent.CompletableFuture;

@Service
@ConditionalOnProperty(prefix = "weatherforecastservice", name = "provider", havingValue = "real", matchIfMissing = true)
public class WeatherForecastServiceImpl implements WeatherForecastService {

    private static final Logger log = LoggerFactory.getLogger(WeatherForecastServiceImpl.class);

    private final RestTemplate restTemplate;
    private final String serviceUrl;

    public WeatherForecastServiceImpl(RestTemplate restTemplate,
                                      @Value("${weatherforecastservice.url}") String serviceUrl) {
        log.debug("Using WeatherForecastServiceImpl as WeatherForecastService implementation");
        this.restTemplate = restTemplate;
        this.serviceUrl = serviceUrl;
    }

    @Override
    @Cacheable("weatherforecast")
    public CompletableFuture<WeatherForecastResponse> getNightForecast(Coordinate coordinate, String timezone) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        if (timezone == null || timezone.isBlank())
            timezone = "Europe/Warsaw";

        URI uri = buildWeatherForecastUrl(coordinate, timezone);
        try {
            WeatherForecastResponse response = restTemplate.getForObject(
                    uri, WeatherForecastResponse.class);

            stopWatch.stop();

            log.info("WeatherForecastService successful response for {} in {}ms",
                    coordinate, stopWatch.getTotalTimeMillis());

            return CompletableFuture.completedFuture(response);
        } catch (HttpClientErrorException.TooManyRequests e) {
            stopWatch.stop();
            log.warn("429 Too Many Requests for coordinate {}: will NOT cache this error", coordinate);
            return CompletableFuture.failedFuture(e);
        } catch (RestClientException e) {
            stopWatch.stop();
            log.error("WeatherForecastService request failed for coordinate {} (URL: {}) in {}ms",
                    coordinate, uri, stopWatch.getTotalTimeMillis(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    private URI buildWeatherForecastUrl(Coordinate coordinate, String timezone) {
        return UriComponentsBuilder.fromUriString(serviceUrl + "/forecast")
                .queryParam("latitude", coordinate.latitude())
                .queryParam("longitude", coordinate.longitude())
                .queryParam("timezone", timezone)
                .build()
                .toUri();
    }
}
