package com.aldhafara.astroSpotFinder.model;

public record ScoringParameters(
        ScoringWeights weights,
        int hourFrom,
        int hourTo
) {
    public static ScoringParameters defaultParameters() {
        return new ScoringParameters(ScoringWeights.defaultWeights(), 21, 6);
    }
}
