package nl.mtvehicles.core.infrastructure.vehicle;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VehicleSeatParsingTest {
    @Test
    void acceptsMixedYamlNumberTypes() {
        Vehicle vehicle = new Vehicle();
        vehicle.setLicensePlate("hz001rp");
        vehicle.setVehicleData(Map.of("seats", List.of(Map.of(
                "x", 0,
                "y", -1L,
                "z", 0.5D))));

        Map<String, Double> seat = vehicle.getSeats().getFirst();

        assertEquals(0.0D, seat.get("x"));
        assertEquals(-1.0D, seat.get("y"));
        assertEquals(0.5D, seat.get("z"));
    }

    @Test
    void rejectsInvalidCoordinatesBeforeSpawningEntities() {
        Vehicle vehicle = new Vehicle();
        vehicle.setLicensePlate("hz001rp");
        vehicle.setVehicleData(Map.of("seats", List.of(Map.of(
                "x", "invalid",
                "y", 0,
                "z", 0))));

        assertThrows(IllegalArgumentException.class, vehicle::getSeats);
    }
}
