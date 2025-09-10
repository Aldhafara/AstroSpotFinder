package com.aldhafara.astroSpotFinder.controller;

import com.aldhafara.astroSpotFinder.model.Coordinate;
import com.aldhafara.astroSpotFinder.model.DarkestLocationsResponse;
import com.aldhafara.astroSpotFinder.model.GridSize;
import com.aldhafara.astroSpotFinder.model.LocationsCluster;
import com.aldhafara.astroSpotFinder.model.ScoringParameters;
import com.aldhafara.astroSpotFinder.model.SearchArea;
import com.aldhafara.astroSpotFinder.model.SearchContext;
import com.aldhafara.astroSpotFinder.model.SearchParams;
import com.aldhafara.astroSpotFinder.model.SimplifiedLocationConditions;
import com.aldhafara.astroSpotFinder.service.AstroSpotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StopWatch;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/astrospots")
@Validated
public class AstroSpotController {

    private static final Logger log = LoggerFactory.getLogger(AstroSpotController.class);

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
    public DarkestLocationsResponse searchBestSpotsWithClusters(
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

        StopWatch stopWatch = new StopWatch();

        stopWatch.start("astroSpotController.searchBestLocationsClusters");
        DarkestLocationsResponse darkestLocationsResponse = astroSpotService.searchBestLocationsClusters(searchParams);
        stopWatch.stop();
        log.info("searchBestLocationsClusters part 0 finished in {} ms", stopWatch.lastTaskInfo().getTimeMillis());

        return darkestLocationsResponse;
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
    public List<SimplifiedLocationConditions> searchBestSpotsScored2(@RequestBody List<LocationsCluster> preliminaryLocationClusters,
                                                                     @RequestParam(required = false) ScoringParameters parameters) {
        try {
            Map<String, List<SimplifiedLocationConditions>> sd = astroSpotService.getBestSpotsWithWeatherScoringClusters(preliminaryLocationClusters, parameters, null).get();
            return sd.get(sd.keySet().stream().findFirst().get());
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
