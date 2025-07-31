package com.aldhafara.astroSpotFinder.model;

import lombok.Builder;

@Builder
public record SearchParams(
        SearchContext searchContext,
        GridSize gridSize,
        int depth,
        SearchArea originSearchArea
) {
}
