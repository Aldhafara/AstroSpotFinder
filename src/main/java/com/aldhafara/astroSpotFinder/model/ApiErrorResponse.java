package com.aldhafara.astroSpotFinder.model;

public record ApiErrorResponse(String timestamp, int status, String error, String message) {}
