package com.aldhafara.javaMicroserviceStarter.model;

public record ApiErrorResponse(String timestamp, int status, String error, String message) {}
