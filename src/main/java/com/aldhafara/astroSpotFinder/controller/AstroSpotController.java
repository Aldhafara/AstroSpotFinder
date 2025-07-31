package com.aldhafara.astroSpotFinder.controller;

import com.aldhafara.astroSpotFinder.model.Coordinate;
import com.aldhafara.astroSpotFinder.model.GridSize;
import com.aldhafara.astroSpotFinder.model.LocationConditions;
import com.aldhafara.astroSpotFinder.model.SearchParams;
import com.aldhafara.astroSpotFinder.model.SearchArea;
import com.aldhafara.astroSpotFinder.model.SearchContext;
import com.aldhafara.astroSpotFinder.service.AstroSpotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/astrospots")
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
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam double radiusKm
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

        return astroSpotService.getTopLocationConditions(locationsConditions);
    }
}
