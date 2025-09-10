package com.aldhafara.astroSpotFinder.model;

import lombok.Getter;

@Getter
public enum DarkestLocationsStatus {
    NO_NEED_TO_GO_DEEPER("There is no need to go deeper."),
    THIS_RESPONSE_IS_ACCURATE("This response is accurate."),
    LIST_BRIGHTEST_SPOTS_IS_EMPTY("List brightestSpots is empty."),
    LIST_GRID_POINTS_IS_EMPTY("List gridPoints is empty."),
    INVALID_PARAMETERS("Invalid parameters."),
    ANSWER_MAY_BE_INACCURATE_PLEASE_TRY_AGAIN_LATER("The answer may be inaccurate, please try again later.");

    private final String message;

    DarkestLocationsStatus(String message) {
        this.message = message;
    }
}
