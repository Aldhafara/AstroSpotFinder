package com.aldhafara.astroSpotFinder.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "astrospot.top")
public record TopLocationsConfig(int number, double percent, boolean extended) {
}
