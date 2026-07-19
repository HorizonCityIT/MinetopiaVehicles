package nl.mtvehicles.core.movement;

import nl.mtvehicles.core.Main;
import nl.mtvehicles.core.infrastructure.vehicle.VehicleData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Paper 1.21.11 movement loop. It reads the public Player Input API once per
 * active driver and replaces all legacy Netty/NMS packet injection.
 */
public final class MovementManager {
    private static final Map<UUID, VehicleMovement> MOVEMENTS = new HashMap<>();
    private static BukkitTask task;

    private MovementManager() {}

    public static void start() {
        if (task != null) return;
        task = Main.instance.getServer().getScheduler().runTaskTimer(Main.instance, MovementManager::tick, 1L, 1L);
    }

    private static void tick() {
        Set<UUID> activeDrivers = new HashSet<>();
        for (ArmorStand driverSeat : VehicleData.autostand2.values()) {
            if (driverSeat == null || !driverSeat.isValid()) continue;
            List<Entity> passengers = driverSeat.getPassengers();
            Entity passenger = passengers.isEmpty() ? null : passengers.getFirst();
            if (!(passenger instanceof Player player) || !player.isOnline()) continue;

            UUID playerId = player.getUniqueId();
            activeDrivers.add(playerId);
            MOVEMENTS.computeIfAbsent(playerId, ignored -> new VehicleMovement())
                    .vehicleMovement(player, player.getCurrentInput());
        }
        MOVEMENTS.keySet().removeIf(playerId -> !activeDrivers.contains(playerId));
    }

    public static void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        MOVEMENTS.clear();
    }
}
