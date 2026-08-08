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

    @Test
    void readsPublicAccessPerSeatWithoutChangingCoordinates() {
        Vehicle vehicle = new Vehicle();
        vehicle.setLicensePlate("hz001rp");
        vehicle.setVehicleData(Map.of("seats", List.of(
                Map.of("x", 0, "y", -1, "z", 0),
                Map.of("x", -1, "y", -1, "z", 0, "public", true))));

        assertEquals(false, vehicle.isSeatPublic(1));
        assertEquals(true, vehicle.isSeatPublic(2));
        assertEquals(-1.0D, vehicle.getSeats().get(1).get("x"));
    }

    @Test
    void supportsPublicSeatsForOneSpecificVehicleSkin() {
        Vehicle vehicle = new Vehicle();
        vehicle.setLicensePlate("police1");
        vehicle.setSkinItem("DIAMOND_HOE");
        vehicle.setSkinDamage(1015);
        vehicle.setVehicleData(Map.of(
                "seats", List.of(
                        Map.of("x", 0, "y", -1, "z", 0),
                        Map.of("x", -1, "y", -1, "z", 0)),
                "cars", List.of(
                        Map.of("SkinItem", "DIAMOND_HOE", "itemDamage", 1003, "publicSeats", List.of(2)),
                        Map.of("SkinItem", "DIAMOND_HOE", "itemDamage", 1015, "publicSeats", List.of(2)))));

        assertEquals(false, vehicle.isSeatPublic(1));
        assertEquals(true, vehicle.isSeatPublic(2));
    }
}
