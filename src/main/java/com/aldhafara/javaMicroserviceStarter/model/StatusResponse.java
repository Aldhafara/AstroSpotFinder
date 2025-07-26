package com.aldhafara.javaMicroserviceStarter.model;

public record StatusResponse(String status, long uptime, String uptimePretty, String timestamp) {
}
