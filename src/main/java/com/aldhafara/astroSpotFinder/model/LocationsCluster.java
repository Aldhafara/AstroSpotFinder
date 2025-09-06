package com.aldhafara.astroSpotFinder.model;

import java.util.Collection;
import java.util.Comparator;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public class LocationsCluster {

    private final SortedSet<LocationConditions> locations;

    public LocationsCluster() {
        this.locations = new TreeSet<>(Comparator.comparingDouble(LocationConditions::brightness));
    }

    public LocationsCluster(Collection<LocationConditions> initialLocations) {
        this.locations = new TreeSet<>(Comparator.comparingDouble(LocationConditions::brightness));
        this.locations.addAll(initialLocations);
    }

    public void add(LocationConditions loc) {
        locations.add(loc);
    }

    public Set<LocationConditions> getLocations() {
        return locations;
    }
}
