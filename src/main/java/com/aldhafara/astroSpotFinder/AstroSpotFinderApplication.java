package com.aldhafara.astroSpotFinder;

import com.aldhafara.astroSpotFinder.configuration.ExecutorConfig;
import com.aldhafara.astroSpotFinder.configuration.TopLocationsConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
@EnableConfigurationProperties({TopLocationsConfig.class, ExecutorConfig.class})
public class AstroSpotFinderApplication {

	public static void main(String[] args) {
		SpringApplication.run(AstroSpotFinderApplication.class, args);
	}

}
