package com.aldhafara.astroSpotFinder.controller;

import com.aldhafara.astroSpotFinder.model.Coordinate;
import com.aldhafara.astroSpotFinder.model.GridSize;
import com.aldhafara.astroSpotFinder.model.LocationConditions;
import com.aldhafara.astroSpotFinder.model.SearchParams;
import com.aldhafara.astroSpotFinder.model.SearchArea;
import com.aldhafara.astroSpotFinder.model.SearchContext;
import com.aldhafara.astroSpotFinder.service.AstroSpotService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
            @RequestParam @Min(0)  @Max(150) double radiusKm,
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
}
