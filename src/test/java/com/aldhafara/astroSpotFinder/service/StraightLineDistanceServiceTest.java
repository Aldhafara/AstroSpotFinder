package com.aldhafara.astroSpotFinder.service;

import com.aldhafara.astroSpotFinder.model.Coordinate;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StraightLineDistanceServiceTest {

    private static final double DELTA_KM = 0.01;
    private StraightLineDistanceService distanceService;
    private Validator validator;

    @BeforeEach
    void setUp() {
        distanceService = new StraightLineDistanceService();
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void testInvalidCoordinate() {
        Coordinate invalid = new Coordinate(100, 200);

        Set<ConstraintViolation<Coordinate>> violations = validator.validate(invalid);
        assertFalse(violations.isEmpty());

        violations.forEach(v -> System.out.println(v.getPropertyPath() + ": " + v.getMessage()));

        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("latitude")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("longitude")));
    }

    @Test
    void testZeroDistance() {
        Coordinate point = new Coordinate(52.2296756, 21.0122287);
        double distance = distanceService.findDistance(point, point);
        assertEquals(0.0, distance, DELTA_KM, "Distance between the same point should be zero");
    }

    @Test
    void testKnownDistanceWarsawToKrakow() {
        Coordinate warsaw = new Coordinate(52.2296756, 21.0122287);
        Coordinate krakow = new Coordinate(50.0616989, 19.9373508);

        double distance = distanceService.findDistance(warsaw, krakow);

        assertEquals(252, distance, 1.0, "Distance Warsaw-Krakow should be about 252 km");
    }

    @Test
    void testKnownDistanceNewYorkToLondon() {
        Coordinate newYork = new Coordinate(40.7128, -74.0060);
        Coordinate london = new Coordinate(51.500741, -0.124607);

        double distance = distanceService.findDistance(newYork, london);

        assertEquals(5570, distance, 10.0, "Distance New York - London should be about 5570 km");
    }

    @Test
    void testAntipodalPoints() {
        Coordinate pointA = new Coordinate(0, 0);
        Coordinate antipode = new Coordinate(-0, 180);

        double distance = distanceService.findDistance(pointA, antipode);

        assertEquals(20015, distance, 10.0, "Distance between antipodal points should be about 20015 km");
    }
}
