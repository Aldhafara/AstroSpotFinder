package com.aldhafara.astroSpotFinder.service;

import com.aldhafara.astroSpotFinder.model.Coordinate;
import com.aldhafara.astroSpotFinder.model.LightPollutionInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class LightPollutionServiceIT {

    @Autowired
    private LightPollutionServiceImpl service;

    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private RestTemplate restTemplate;

    @Test
    void testGetLightPollution_withCaching() {
        Coordinate coord = new Coordinate(10, 20);
        LightPollutionInfo response = new LightPollutionInfo(10, 20, 100);

        when(restTemplate.getForObject(any(), eq(LightPollutionInfo.class)))
                .thenReturn(response);

        Optional<LightPollutionInfo> firstResult = service.getLightPollution(coord);
        assertThat(firstResult).isPresent();
        assertThat(firstResult.get().relativeBrightness()).isEqualTo(100);

        verify(restTemplate, times(1))
                .getForObject(any(), eq(LightPollutionInfo.class));

        Optional<LightPollutionInfo> secondResult = service.getLightPollution(coord);
        assertThat(secondResult).isPresent();
        assertThat(secondResult.get().relativeBrightness()).isEqualTo(100);

        verify(restTemplate, times(1))
                .getForObject(any(), eq(LightPollutionInfo.class));

        var cache = cacheManager.getCache("lightPollution");
        assertThat(cache).isNotNull();
        var cachedValue = cache.get(coord, LightPollutionInfo.class);
        assertThat(cachedValue).isNotNull();
        assertThat(cachedValue).isEqualTo(secondResult.get());
    }
}
