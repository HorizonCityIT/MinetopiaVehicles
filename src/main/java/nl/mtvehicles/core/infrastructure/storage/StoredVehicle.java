package nl.mtvehicles.core.infrastructure.storage;

/** Immutable database representation of one vehicle. */
public record StoredVehicle(
        String licensePlate,
        String ownerUuid,
        String vehicleType,
        String skinItem,
        Integer skinDamage,
        String payload
) {}
