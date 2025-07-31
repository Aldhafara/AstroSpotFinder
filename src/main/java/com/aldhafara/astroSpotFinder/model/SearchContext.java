package com.aldhafara.astroSpotFinder.model;

import lombok.Builder;

@Builder
public record SearchContext(
        int maxDepth,
        int gridDiv,
        SearchArea searchArea
) {
}
