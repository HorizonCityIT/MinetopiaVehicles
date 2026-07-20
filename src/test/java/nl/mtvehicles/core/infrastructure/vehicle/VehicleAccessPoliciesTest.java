package nl.mtvehicles.core.infrastructure.vehicle;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleAccessPoliciesTest {

    @AfterEach
    void clearPolicies() {
        VehicleAccessPolicies.clear();
    }

    @Test
    void everyDrivingPolicyMustAllowTheDriver() {
        VehicleAccessPolicies.registerDrivingPolicy("allow", (plate, player) -> true);
        VehicleAccessPolicies.registerDrivingPolicy("showroom", (plate, player) -> !plate.equals("hz001rp"));

        assertTrue(VehicleAccessPolicies.isDrivingAllowed("hz002rp", null));
        assertFalse(VehicleAccessPolicies.isDrivingAllowed("hz001rp", null));
    }

    @Test
    void anyEntryOverrideCanOpenAPrivateVehicle() {
        VehicleAccessPolicies.registerEntryOverride("unrelated", (plate, player) -> false);
        VehicleAccessPolicies.registerEntryOverride("showroom", (plate, player) -> plate.equals("hz001rp"));

        assertTrue(VehicleAccessPolicies.hasEntryOverride("hz001rp", null));
        assertFalse(VehicleAccessPolicies.hasEntryOverride("hz002rp", null));
    }

    @Test
    void unregisterRemovesBothKindsOfPolicy() {
        VehicleAccessPolicies.registerDrivingPolicy("showroom", (plate, player) -> false);
        VehicleAccessPolicies.registerEntryOverride("showroom", (plate, player) -> true);

        VehicleAccessPolicies.unregister("showroom");

        assertTrue(VehicleAccessPolicies.isDrivingAllowed("hz001rp", null));
        assertFalse(VehicleAccessPolicies.hasEntryOverride("hz001rp", null));
    }
}
