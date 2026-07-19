package nl.mtvehicles.core.infrastructure.vehicle;

import nl.mtvehicles.core.infrastructure.annotations.ToDo;
import nl.mtvehicles.core.infrastructure.enums.VehicleType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps containing vehicles' data
 */
@ToDo("make nicer, move, refactor - for better API usage")
public class VehicleData {
    /**
     * @see Vehicle#getCurrentSpeed()
     */
    public static HashMap<String, Double> speed = new HashMap<>();
    public static HashMap<String, Integer> maxheight = new HashMap<>();
    public static HashMap<String, Double> mainx = new HashMap<>();
    public static HashMap<String, Double> mainy = new HashMap<>();
    public static HashMap<String, Double> mainz = new HashMap<>();
    public static HashMap<String, Integer> seatsize = new HashMap<>();
    public static HashMap<String, Double> seatx = new HashMap<>();
    public static HashMap<String, Double> seaty = new HashMap<>();
    public static HashMap<String, Double> seatz = new HashMap<>();
    public static HashMap<String, Double> wiekenx = new HashMap<>();
    public static HashMap<String, Double> wiekeny = new HashMap<>();
    public static HashMap<String, Double> wiekenz = new HashMap<>();
    public static HashMap<String, VehicleType> type = new HashMap<>();
    /**
     * @see Vehicle#getCurrentFuel()
     */
    public static HashMap<String, Double> fuel = new HashMap<>();
    public static HashMap<String, Double> fuelUsage = new HashMap<>();
    public static HashMap<String, ArmorStand> autostand = new HashMap<>();
    public static Map<String, ArmorStand> autostand2 = new ConcurrentHashMap<>();
    public static Map<String, Long> lastUsage = new HashMap<>();
    public static HashMap<String, Boolean> fallDamage = new HashMap<>(); //Used for helicopters when 'extremely falling'

    private static HashMap<String, Integer> RotationSpeed = new HashMap<>();
    private static HashMap<String, Double> MaxSpeed = new HashMap<>();
    private static HashMap<String, Double> AccelerationSpeed = new HashMap<>();
    private static HashMap<String, Double> BrakingSpeed = new HashMap<>();
    private static HashMap<String, Double> MaxSpeedBackwards = new HashMap<>();
    private static HashMap<String, Double> FrictionSpeed = new HashMap<>();
    public static Set<String> frictionBlocked = new HashSet<>();
    public static Set<String> brakingBlocked = new HashSet<>();
    public static HashMap<String, Set<String>> lastRegions = new HashMap<>();
    public static HashMap<String, Location> lastRegionCheckLocation = new HashMap<>();
    public static HashMap<String, Boolean> destroyedVehicles = new HashMap<>();
    /**
     * @see VehicleUtils#openedTrunk
     * @since 2.5.1
     */
    private static HashMap<String, Set<Player>> trunkViewers = new HashMap<>();

    private VehicleData(){}

    /**
     * Check if a player has an opened trunk
     * @param licensePlate Vehicle's license plate (this vehicle's trunk is being opened)
     * @param player Player
     * @since 2.5.1
     */
    public static boolean isTrunkViewer(String licensePlate, Player player){
        setTrunkViewers(licensePlate);
        return trunkViewers.get(licensePlate).contains(player);
    }

    public enum DataSpeed {
        MAXSPEED, ACCELERATION, BRAKING, MAXSPEEDBACKWARDS, FRICTION
    }

    public static Double getSpeed(@NotNull DataSpeed speedType, @NotNull String licensePlate) {
        return switch (speedType) {
            case MAXSPEED -> MaxSpeed.get(licensePlate);
            case ACCELERATION -> AccelerationSpeed.get(licensePlate);
            case BRAKING -> brakingBlocked.contains(licensePlate) ? 0.0D : BrakingSpeed.get(licensePlate);
            case MAXSPEEDBACKWARDS -> MaxSpeedBackwards.get(licensePlate);
            case FRICTION -> frictionBlocked.contains(licensePlate) ? 0.0D : FrictionSpeed.get(licensePlate);
        };
    }

    public static Integer getRotationSpeed(String licensePlate) {
        return RotationSpeed.get(licensePlate);
    }

    public static void setSpeed(@NotNull DataSpeed speedType, @NotNull String licensePlate, @NotNull Double value) {
        switch (speedType) {
            case MAXSPEED -> MaxSpeed.put(licensePlate, value);
            case ACCELERATION -> AccelerationSpeed.put(licensePlate, value);
            case BRAKING -> BrakingSpeed.put(licensePlate, value);
            case MAXSPEEDBACKWARDS -> MaxSpeedBackwards.put(licensePlate, value);
            case FRICTION -> FrictionSpeed.put(licensePlate, value);
        }
    }

    public static void setRotationSpeed(String licensePlate, Integer value) {
        RotationSpeed.put(licensePlate, value);
    }

    /**
     * Remove per-drive data once a vehicle is no longer active.
     * Parked entity references can be retained so API location lookups keep working.
     */
    public static void clearRuntimeData(String licensePlate, boolean removeEntityReferences) {
        speed.remove(licensePlate);
        maxheight.remove(licensePlate);
        seatsize.remove(licensePlate);
        type.remove(licensePlate);
        fuel.remove(licensePlate);
        fuelUsage.remove(licensePlate);
        autostand2.remove(licensePlate);
        RotationSpeed.remove(licensePlate);
        MaxSpeed.remove(licensePlate);
        AccelerationSpeed.remove(licensePlate);
        BrakingSpeed.remove(licensePlate);
        MaxSpeedBackwards.remove(licensePlate);
        FrictionSpeed.remove(licensePlate);
        frictionBlocked.remove(licensePlate);
        brakingBlocked.remove(licensePlate);
        lastRegions.remove(licensePlate);
        lastRegionCheckLocation.remove(licensePlate);

        String suffix = "_" + licensePlate;
        mainx.keySet().removeIf(key -> key.endsWith(suffix));
        mainy.keySet().removeIf(key -> key.endsWith(suffix));
        mainz.keySet().removeIf(key -> key.endsWith(suffix));
        seatx.keySet().removeIf(key -> key.endsWith(suffix));
        seaty.keySet().removeIf(key -> key.endsWith(suffix));
        seatz.keySet().removeIf(key -> key.endsWith(suffix));
        wiekenx.keySet().removeIf(key -> key.endsWith(suffix));
        wiekeny.keySet().removeIf(key -> key.endsWith(suffix));
        wiekenz.keySet().removeIf(key -> key.endsWith(suffix));

        autostand.entrySet().removeIf(entry -> entry.getKey().startsWith("MTVEHICLES_SEAT")
                && entry.getKey().endsWith(suffix));
        if (removeEntityReferences) {
            autostand.keySet().removeIf(key -> key.endsWith(suffix));
            fallDamage.remove(licensePlate);
            destroyedVehicles.remove(licensePlate);
            trunkViewers.remove(licensePlate);
        }
    }

    /**
     * Add a player to list of players who have vehicle's trunk inventory opened
     * @param licensePlate Vehicle's license plate
     * @param player Player who's being added to the set
     * @return Returns {@code false} if player is already present in the set
     * @since 2.5.1
     */
    public static boolean trunkViewerAdd(String licensePlate, Player player){
        setTrunkViewers(licensePlate);
        Set<Player> viewers = trunkViewers.get(licensePlate);
        boolean returns = viewers.add(player);
        trunkViewers.put(licensePlate, viewers);
        return returns;
    }

    /**
     * Remove a player from list of players who have vehicle's trunk inventory opened
     * @param licensePlate Vehicle's license plate
     * @param player Player who's being removed from the set
     * @return Returns {@code false} if player is not present in the set
     * @since 2.5.1
     */
    public static boolean trunkViewerRemove(String licensePlate, Player player){
        setTrunkViewers(licensePlate);
        Set<Player> viewers = trunkViewers.get(licensePlate);
        boolean returns = viewers.remove(player);
        trunkViewers.put(licensePlate, viewers);
        return returns;
    }

    /** Remove a disconnecting player from every tracked trunk view. */
    public static void removeTrunkViewer(Player player) {
        trunkViewers.values().forEach(viewers -> viewers.remove(player));
        trunkViewers.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    /**
     * Get a HashSet of all trunk viewers of a vehicle
     * @param licensePlate Vehicle's license plate
     */
    public static Set<Player> getTrunkViewers(String licensePlate){
        setTrunkViewers(licensePlate);
        return trunkViewers.get(licensePlate);
    }

    /**
     * Prevent issues if {@link #trunkViewers} is not initialised
     */
    private static void setTrunkViewers(String licensePlate){
        if (!trunkViewers.containsKey(licensePlate)) trunkViewers.put(licensePlate, new HashSet<>());
    }

    /**
     * Mark a vehicle as destroyed
     * @param licensePlate
     */
    public static void markVehicleAsDestroyed(String licensePlate) {
        destroyedVehicles.put(licensePlate, true);
    }

    /**
     * Mark vehicle as repaired
     * @param licensePlate
     */
    public static void markVehicleAsRepaired(String licensePlate) {
        destroyedVehicles.remove(licensePlate);
    }

    /**
     * Check if a vehicle is destroyed
     * @param licensePlate
     * @return Boolean
     */
    public static boolean isVehicleDestroyed(String licensePlate) {
        return destroyedVehicles.getOrDefault(licensePlate, false);
    }

}
