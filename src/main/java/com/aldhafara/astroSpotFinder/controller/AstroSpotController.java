package com.aldhafara.astroSpotFinder.controller;

import com.aldhafara.astroSpotFinder.model.Coordinate;
import com.aldhafara.astroSpotFinder.model.GridSize;
import com.aldhafara.astroSpotFinder.model.LocationConditions;
import com.aldhafara.astroSpotFinder.model.ScoringParameters;
import com.aldhafara.astroSpotFinder.model.SearchArea;
import com.aldhafara.astroSpotFinder.model.SearchContext;
import com.aldhafara.astroSpotFinder.model.SearchParams;
import com.aldhafara.astroSpotFinder.model.SimplifiedLocationConditions;
import com.aldhafara.astroSpotFinder.service.AstroSpotService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/astrospots")
@Validated
public class AstroSpotController {

    private final double gridLatDeg;
    private final double gridLonDeg;
    private final int gridDiv;
    private final int maxDepth;

    private final AstroSpotService astroSpotService;

    @Autowired
    public AstroSpotController(AstroSpotService astroSpotService,
                               @Value("${astrospot.grid.latitude.size}") double gridLatDeg,
                               @Value("${astrospot.grid.longitude.size}") double gridLonDeg,
                               @Value("${astrospot.grid.step.divisor}") int gridDiv,
                               @Value("${astrospot.grid.depth.max}") int maxDepth) {
        this.astroSpotService = astroSpotService;
        this.gridLatDeg = gridLatDeg;
        this.gridLonDeg = gridLonDeg;
        this.gridDiv = gridDiv;
        this.maxDepth = maxDepth;
    }

    @GetMapping("/best")
    public List<LocationConditions> searchBestSpots(
            @RequestParam @Min(-90) @Max(90) double latitude,
            @RequestParam @Min(-180) @Max(180) double longitude,
            @RequestParam @Min(0) @Max(150) double radiusKm,
            @RequestParam(required = false, defaultValue = "100") @Min(0) int maxResults
    ) {
        Coordinate center = new Coordinate(latitude, longitude);
        SearchArea searchArea = SearchArea.builder()
                .center(center)
                .radiusKm(radiusKm)
                .build();
        SearchContext searchContext = SearchContext.builder()
                .maxDepth(maxDepth)
                .gridDiv(gridDiv)
                .searchArea(searchArea)
                .build();
        GridSize gridSize = GridSize.builder()
                .latitudeDegrees(gridLatDeg)
                .longitudeDegrees(gridLonDeg)
                .build();

        SearchParams searchParams = SearchParams.builder()
                .searchContext(searchContext)
                .gridSize(gridSize)
                .depth(0)
                .originSearchArea(searchArea)
                .build();

        List<LocationConditions> locationsConditions = astroSpotService.searchBestSpotsRecursive(searchParams);

        var results = astroSpotService.getTopLocationConditions(locationsConditions);
        if (results.size() > maxResults) {
            return results.subList(0, maxResults);
        } else {
            return results;
        }
    }

    @Operation(
            summary = "Get best scored spots with weather data",
            description = "Accepts a list of preliminary location spots and optional scoring parameters, " +
                    "returns the best spots scored with weather and other factors.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "List of best scored spots grouped by period",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(
                                            implementation = SimplifiedLocationConditions.class,
                                            type = "array"
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Internal server error - scoring process failed"
                    )
            }
    )
    @PostMapping("/best-scored")
    public List<SimplifiedLocationConditions> searchBestSpotsScored(@RequestBody List<LocationConditions> preliminarySpots,
                                                                    @RequestParam(required = false) ScoringParameters parameters) {
        try {
            var sd = astroSpotService.getBestSpotsWithWeatherScoring(preliminarySpots, parameters, null).get();
            return sd.get(sd.keySet().stream().findFirst().get());
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
