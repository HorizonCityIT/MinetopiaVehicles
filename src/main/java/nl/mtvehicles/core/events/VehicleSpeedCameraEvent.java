package nl.mtvehicles.core.events;

import nl.mtvehicles.core.events.interfaces.HasVehicle;
import nl.mtvehicles.core.infrastructure.models.MTVEvent;
import org.bukkit.Location;

/** Fired when a static speed camera detects a vehicle above its configured limit. */
public class VehicleSpeedCameraEvent extends MTVEvent implements HasVehicle {
    private final String cameraId;
    private final String licensePlate;
    private final double speedKmh;
    private final double limitKmh;
    private final Location cameraLocation;

    public VehicleSpeedCameraEvent(String cameraId, String licensePlate, double speedKmh,
                                   double limitKmh, Location cameraLocation) {
        this.cameraId = cameraId;
        this.licensePlate = licensePlate;
        this.speedKmh = speedKmh;
        this.limitKmh = limitKmh;
        this.cameraLocation = cameraLocation.clone();
    }

    public String getCameraId() {
        return cameraId;
    }

    @Override
    public String getLicensePlate() {
        return licensePlate;
    }

    public double getSpeedKmh() {
        return speedKmh;
    }

    public double getLimitKmh() {
        return limitKmh;
    }

    public Location getCameraLocation() {
        return cameraLocation.clone();
    }
}
