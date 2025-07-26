package com.aldhafara.javaMicroserviceStarter.controller;

import com.aldhafara.javaMicroserviceStarter.model.StatusResponse;
import com.aldhafara.javaMicroserviceStarter.service.StatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@RestController
public class StatusController {
    private final StatusService statusService;

    public StatusController(StatusService statusService) {
        this.statusService = statusService;
    }

    @Operation(
            summary = "Service health-check",
            description = "Returns the server's uptime status, uptime and timestamp.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Service is working",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = StatusResponse.class)
                            )
                    )
            }
    )
    @GetMapping("/status")
    public StatusResponse getStatus() {

        long uptimeMillis = statusService.getUptimeMillis();
        return new StatusResponse(
                "UP",
                uptimeMillis,
                getUptimePretty(uptimeMillis),
                statusService.getTimestamp()
        );
    }

    private String getUptimePretty(long uptimeMillis) {
        int totalSeconds = (int) (uptimeMillis / 1000);
        int days = totalSeconds / 86400;
        int hours = (totalSeconds % 86400) / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        sb.append(hours).append("h ")
                .append(minutes).append("m ")
                .append(seconds).append("s");
        return sb.toString();
    }
}
