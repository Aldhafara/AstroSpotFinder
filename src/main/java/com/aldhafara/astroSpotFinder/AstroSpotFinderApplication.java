package com.aldhafara.astroSpotFinder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class AstroSpotFinderApplication {

	public static void main(String[] args) {
		SpringApplication.run(AstroSpotFinderApplication.class, args);
	}

}
