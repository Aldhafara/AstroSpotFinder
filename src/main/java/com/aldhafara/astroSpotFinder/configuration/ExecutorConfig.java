package com.aldhafara.astroSpotFinder.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "astrospot.executor.max.concurrent")
public record ExecutorConfig(int threads) {
}
