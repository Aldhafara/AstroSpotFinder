package com.aldhafara.astroSpotFinder.model;

public record ScoringWeights(
        double wLightPollution,
        double wDistance,
        double wCloudCover,
        double wVisibility,
        double wWindSpeed,
        double wWindGust
) {
    public static ScoringWeights defaultWeights() {
        return new ScoringWeights(0.4, 0.2, 0.15, 0.15, 0.05, 0.05);
    }
}
