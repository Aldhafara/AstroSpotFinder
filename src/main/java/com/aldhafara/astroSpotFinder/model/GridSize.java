package com.aldhafara.astroSpotFinder.model;
import lombok.Builder;

@Builder
public record GridSize(double latitudeDegrees, double longitudeDegrees) {}
