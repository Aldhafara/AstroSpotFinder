package com.aldhafara.astroSpotFinder.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Set;

@Getter
@AllArgsConstructor
public class LocationsWithBrightnessResponse {
    Set<LocationConditions> locationsWithBrightness;
    Set<String> additionalMessages;
}
