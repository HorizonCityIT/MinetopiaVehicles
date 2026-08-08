package nl.mtvehicles.core.infrastructure.vehicle;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VehicleSeatLocationTest {
    @Test
    void appliesForwardAndLateralOffsetsAtZeroYaw() {
        Location location = new Location(null, 10.0D, 64.0D, 20.0D, 0.0F, 0.0F);

        Location seat = VehicleUtils.getSeatLocation(location, -1.0D, -1.25D, 0.5D);

        assertEquals(10.5D, seat.getX(), 0.00001D);
        assertEquals(62.75D, seat.getY(), 0.00001D);
        assertEquals(19.0D, seat.getZ(), 0.00001D);
    }

    @Test
    void rotatesSeatOffsetsWithVehicleYaw() {
        Location location = new Location(null, 10.0D, 64.0D, 20.0D, 90.0F, 0.0F);

        Location seat = VehicleUtils.getSeatLocation(location, -1.0D, 0.0D, 0.5D);

        assertEquals(11.0D, seat.getX(), 0.00001D);
        assertEquals(20.5D, seat.getZ(), 0.00001D);
    }
}
