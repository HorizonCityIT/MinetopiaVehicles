package nl.mtvehicles.core.infrastructure.dataconfig;

import nl.mtvehicles.core.Main;
import nl.mtvehicles.core.infrastructure.enums.ConfigType;
import nl.mtvehicles.core.infrastructure.enums.VehicleType;
import nl.mtvehicles.core.infrastructure.models.MTVConfig;
import nl.mtvehicles.core.infrastructure.storage.AsyncVehicleWriteQueue;
import nl.mtvehicles.core.infrastructure.storage.MariaDbVehicleStorage;
import nl.mtvehicles.core.infrastructure.storage.StoredVehicle;
import nl.mtvehicles.core.infrastructure.vehicle.Vehicle;
import nl.mtvehicles.core.infrastructure.vehicle.VehicleUtils;
import nl.mtvehicles.core.infrastructure.modules.ConfigModule;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Methods for supersecretsettings.yml.
 * Do not initialise this class directly. Use {@link ConfigModule#vehicleDataConfig} instead.
 */
public class VehicleDataConfig extends MTVConfig {


    private Map<String, ConfigurationSection> vehicleDataInMemory;
    private final Map<String, Integer> vehiclesPerOwner = new HashMap<>();
    private final Set<String> dirtyVehicles = new HashSet<>();
    private final Set<String> deletedVehicles = new HashSet<>();
    private final Set<String> registrationsInProgress = new HashSet<>();
    private final LicensePlateAllocator licensePlateAllocator = new LicensePlateAllocator();
    private BukkitRunnable saveTask;
    private boolean dirty;
    private boolean mariaDbActive;
    private AsyncVehicleWriteQueue writeQueue;


    /**
     * Default constructor - do not use this.
     * Use {@link ConfigModule#vehicleDataConfig} instead.
     */
    public VehicleDataConfig() {
        super(ConfigType.VEHICLE_DATA);
        this.vehicleDataInMemory = new HashMap<>();
        loadFromDisk();
        startAutoSaveTask(20L * 60L * 10L);
    }

    /**
     * Load vehicle data from disk into memory
     */
    private void loadFromDisk() {
        vehicleDataInMemory.clear();
        ConfigurationSection vehiclesSection = getConfiguration().getConfigurationSection("vehicle");
        if (vehiclesSection != null) {
            for (String licensePlate : vehiclesSection.getKeys(false)) {
                ConfigurationSection vehicle = vehiclesSection.getConfigurationSection(licensePlate);
                if (vehicle != null) vehicleDataInMemory.put(licensePlate, vehicle);
            }
        }
        rebuildOwnerIndex();
        licensePlateAllocator.reset(vehicleDataInMemory.keySet());
        dirty = false;
        dirtyVehicles.clear();
        deletedVehicles.clear();
    }

    /**
     * Reload both the YAML configuration and the in-memory vehicle index.
     */
    @Override
    public void reload() {
        // Changing a live storage backend would invalidate queued writes. It is
        // deliberately applied only on the next full server restart.
        if (mariaDbActive) return;
        super.reload();
        if (vehicleDataInMemory != null) loadFromDisk();
    }

    /**
     * Start a task to save data to disk every 10 minutes
     */
    private void startAutoSaveTask(long intervalTicks) {
        if (saveTask != null) saveTask.cancel();
        saveTask = new BukkitRunnable() {
            @Override
            public void run() {
                saveToDisk();
            }
        };
        saveTask.runTaskTimer(Main.instance, intervalTicks, intervalTicks);
    }

    /**
     * Enables MariaDB after all configuration files have been loaded. A failed
     * connection is required when MariaDB is selected.
     */
    public boolean initializeStorage(StorageConfig storageConfig) {
        if (storageConfig.getStorageType() != StorageConfig.StorageType.MARIADB) {
            Main.logInfo("Persistent vehicle storage: YAML (compatibility mode).");
            return true;
        }

        MariaDbVehicleStorage candidate = null;
        try {
            candidate = new MariaDbVehicleStorage(storageConfig);
            recoverEmergencyFile(candidate);
            Map<String, StoredVehicle> storedVehicles = candidate.loadAll();
            loadFromStoredVehicles(storedVehicles);

            this.writeQueue = new AsyncVehicleWriteQueue(candidate);
            this.mariaDbActive = true;
            this.dirty = false;
            this.dirtyVehicles.clear();
            this.deletedVehicles.clear();
            startAutoSaveTask(storageConfig.getFlushIntervalTicks());
            Main.logInfo("Persistent vehicle storage: MariaDB (" + storedVehicles.size()
                    + " vehicles cached in memory, asynchronous write-behind enabled).");
            return true;
        } catch (Exception exception) {
            if (candidate != null) candidate.close();
            this.writeQueue = null;
            this.mariaDbActive = false;
            Main.instance.getLogger().log(Level.SEVERE, "Could not start MariaDB storage", exception);
            Main.logSevere("MariaDB is configured as authoritative storage. The plugin will not start with stale YAML data.");
            return false;
        }
    }

    /**
     * Save all vehicle data from memory to disk
     */
    public void saveToDisk() {
        if (!dirty) {
            if (mariaDbActive && writeQueue != null) writeQueue.requestFlush();
            return;
        }
        if (mariaDbActive) {
            List<StoredVehicle> upserts = new ArrayList<>();
            for (String licensePlate : new HashSet<>(dirtyVehicles)) {
                ConfigurationSection section = vehicleDataInMemory.get(licensePlate);
                if (section != null) upserts.add(toStoredVehicle(licensePlate, section));
            }
            Set<String> deletes = new HashSet<>(deletedVehicles);
            writeQueue.enqueue(upserts, deletes);
            dirtyVehicles.clear();
            deletedVehicles.clear();
            dirty = false;
            writeQueue.requestFlush();
            return;
        }
        clearFile(false);
        for (Map.Entry<String, ConfigurationSection> entry : vehicleDataInMemory.entrySet()) {
            getConfiguration().set("vehicle." + entry.getKey(), entry.getValue());
        }
        boolean saved = super.save();
        dirty = !saved;
        if (saved) {
            dirtyVehicles.clear();
            deletedVehicles.clear();
        }
    }

    /**
     * Persist the in-memory representation instead of writing a potentially
     * stale configuration snapshot.
     */
    @Override
    public boolean save() {
        if (!dirty) return true;
        saveToDisk();
        return !dirty;
    }

    /**
     * Clear the whole vehicleData file (to be used while saving new data from memory to disk)
     * @param save Whether to save the configuration file afterwards
     */
    private void clearFile(boolean save){
        getConfiguration().set("vehicle", null);
        if (save) save();
    }

    /**
     * Get a data option of a vehicle from in-memory data
     *
     * @param licensePlate Vehicle's license plate
     * @param dataOption   Data option of a vehicle
     * @return Value of the option (as Object)
     */
    public Object get(String licensePlate, Option dataOption) {
        ConfigurationSection vehicleSection = vehicleDataInMemory.getOrDefault(licensePlate, null);
        if (vehicleSection == null) {
            return null;
        }
        return vehicleSection.get(dataOption.getPath());
    }

    /** Whether a registered plate exists, using MariaDB-compatible case-insensitive matching. */
    public synchronized boolean containsLicensePlate(String licensePlate) {
        return licensePlateAllocator.isRegistered(licensePlate);
    }

    /** Reserve the lowest available {@code hz###rp} plate for a pending delivery. */
    public synchronized String reserveNextLicensePlate() {
        return licensePlateAllocator.reserveNext();
    }

    /** Release a plate when its pending item could not be delivered and registered. */
    public synchronized void releaseLicensePlateReservation(String licensePlate) {
        licensePlateAllocator.releaseReservation(licensePlate);
    }


    /**
     * Set a data option of a vehicle in memory
     *
     * @param licensePlate Vehicle's license plate
     * @param dataOption   Data option of a vehicle
     * @param value        New value of the option (should be the same type!)
     */
    public void set(String licensePlate, Option dataOption, Object value) {
        ConfigurationSection vehicleSection = vehicleDataInMemory.get(licensePlate);
        if (vehicleSection == null) {
            if (licensePlate == null || !registrationsInProgress.contains(licensePlate.toLowerCase(Locale.ROOT))) {
                throw new IllegalStateException("Vehicle " + licensePlate + " is not registered");
            }
            vehicleSection = getConfiguration().createSection("vehicle." + licensePlate);
            vehicleDataInMemory.put(licensePlate, vehicleSection);
        }
        String previousOwner = dataOption == Option.OWNER ? vehicleSection.getString(Option.OWNER.getPath()) : null;
        vehicleSection.set(dataOption.getPath(), value);
        if (dataOption == Option.OWNER) {
            String nextOwner = value == null ? null : value.toString();
            updateOwnerIndex(previousOwner, nextOwner);
        }
        deletedVehicles.remove(licensePlate);
        dirtyVehicles.add(licensePlate);
        dirty = true;
    }

    /** Execute the initial write as one guarded registration operation. */
    public synchronized void registerNewVehicle(String licensePlate, Runnable writer) {
        if (licensePlate == null || licensePlate.isBlank()) throw new IllegalArgumentException("Missing license plate");
        if (licensePlateAllocator.isRegistered(licensePlate)) {
            throw new IllegalStateException("Vehicle " + licensePlate + " is already registered");
        }
        String normalizedPlate = licensePlate.toLowerCase(Locale.ROOT);
        if (!registrationsInProgress.add(normalizedPlate)) {
            throw new IllegalStateException("Vehicle " + licensePlate + " is already being registered");
        }
        try {
            writer.run();
            if (!vehicleDataInMemory.containsKey(licensePlate)) {
                throw new IllegalStateException("Vehicle registration produced no data");
            }
            licensePlateAllocator.addRegistered(licensePlate);
            if (mariaDbActive) saveToDisk();
        } catch (RuntimeException exception) {
            vehicleDataInMemory.remove(licensePlate);
            getConfiguration().set("vehicle." + licensePlate, null);
            dirtyVehicles.remove(licensePlate);
            deletedVehicles.remove(licensePlate);
            licensePlateAllocator.removeRegistered(licensePlate);
            licensePlateAllocator.releaseReservation(licensePlate);
            rebuildOwnerIndex();
            throw exception;
        } finally {
            registrationsInProgress.remove(normalizedPlate);
        }
    }

    /** Rename one existing identity without creating a second independent record. */
    public synchronized void renameVehicle(String currentPlate, String newPlate) {
        if (currentPlate == null || newPlate == null || newPlate.isBlank()) {
            throw new IllegalArgumentException("Invalid license plate");
        }
        ConfigurationSection source = vehicleDataInMemory.get(currentPlate);
        if (source == null) throw new IllegalStateException("Vehicle " + currentPlate + " is not registered");
        if (currentPlate.equals(newPlate)) return;
        if (!currentPlate.equalsIgnoreCase(newPlate) && licensePlateAllocator.isUnavailable(newPlate)) {
            throw new IllegalStateException("Vehicle " + newPlate + " is already registered");
        }

        ConfigurationSection target = getConfiguration().createSection("vehicle." + newPlate);
        copySection(source, target);
        vehicleDataInMemory.put(newPlate, target);
        vehicleDataInMemory.remove(currentPlate);
        getConfiguration().set("vehicle." + currentPlate, null);
        licensePlateAllocator.removeRegistered(currentPlate);
        licensePlateAllocator.addRegistered(newPlate);

        dirtyVehicles.remove(currentPlate);
        dirtyVehicles.add(newPlate);
        deletedVehicles.add(currentPlate);
        dirty = true;
        saveToDisk();
    }

    /**
     * Delete a vehicle from in-memory data
     *
     * @param licensePlate Vehicle's license plate
     * @throws IllegalStateException If vehicle is already deleted.
     */
    public synchronized void delete(String licensePlate) throws IllegalStateException {
        if (!vehicleDataInMemory.containsKey(licensePlate)) {
            throw new IllegalStateException("An error occurred while trying to delete a vehicle. Vehicle is already deleted.");
        }
        ConfigurationSection removed = vehicleDataInMemory.remove(licensePlate);
        if (removed != null) updateOwnerIndex(removed.getString(Option.OWNER.getPath()), null);
        licensePlateAllocator.removeRegistered(licensePlate);
        dirtyVehicles.remove(licensePlate);
        deletedVehicles.add(licensePlate);
        dirty = true;
        saveToDisk();
    }

    /** Flush pending writes and close the pool. Writes an emergency snapshot if MariaDB is unavailable. */
    public void shutdownStorage() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        if (!mariaDbActive || writeQueue == null) {
            saveToDisk();
            return;
        }

        saveToDisk();
        boolean flushed = writeQueue.flushBlocking(10L, TimeUnit.SECONDS);
        if (!flushed) {
            saveEmergencySnapshot();
            Main.logWarning("MariaDB still had " + writeQueue.getPendingWriteCount()
                    + " queued writes. A recovery snapshot was saved locally.");
        }
        writeQueue.close();
        writeQueue = null;
        mariaDbActive = false;
    }

    public boolean isMariaDbActive() {
        return mariaDbActive;
    }

    private StoredVehicle toStoredVehicle(String licensePlate, ConfigurationSection section) {
        YamlConfiguration payload = new YamlConfiguration();
        ConfigurationSection data = payload.createSection("data");
        copySection(section, data);
        Object damage = section.get(Option.SKIN_DAMAGE.getPath());
        Integer skinDamage = damage instanceof Number ? ((Number) damage).intValue() : null;
        return new StoredVehicle(licensePlate,
                section.getString(Option.OWNER.getPath()),
                section.getString(Option.VEHICLE_TYPE.getPath()),
                section.getString(Option.SKIN_ITEM.getPath()),
                skinDamage, payload.saveToString());
    }

    private void loadFromStoredVehicles(Map<String, StoredVehicle> vehicles) {
        vehicleDataInMemory.clear();
        getConfiguration().set("vehicle", null);
        int invalid = 0;
        for (StoredVehicle stored : vehicles.values()) {
            try {
                YamlConfiguration payload = new YamlConfiguration();
                payload.loadFromString(stored.payload());
                ConfigurationSection source = payload.getConfigurationSection("data");
                if (source == null) throw new InvalidConfigurationException("missing data section");
                ConfigurationSection target = getConfiguration().createSection("vehicle." + stored.licensePlate());
                copySection(source, target);
                vehicleDataInMemory.put(stored.licensePlate(), target);
            } catch (InvalidConfigurationException exception) {
                invalid++;
                Main.instance.getLogger().severe("Invalid MariaDB payload for vehicle "
                        + stored.licensePlate() + ": " + exception.getMessage());
            }
        }
        rebuildOwnerIndex();
        licensePlateAllocator.reset(vehicleDataInMemory.keySet());
        if (invalid > 0) Main.logWarning(invalid + " invalid MariaDB vehicle record(s) were skipped; the rows were not deleted.");
    }

    private void rebuildOwnerIndex() {
        vehiclesPerOwner.clear();
        for (ConfigurationSection section : vehicleDataInMemory.values()) {
            if (section == null) continue;
            String owner = section.getString(Option.OWNER.getPath());
            if (owner != null) vehiclesPerOwner.put(owner, vehiclesPerOwner.getOrDefault(owner, 0) + 1);
        }
    }

    private void updateOwnerIndex(String previousOwner, String nextOwner) {
        if (Objects.equals(previousOwner, nextOwner)) return;
        if (previousOwner != null) {
            int remaining = vehiclesPerOwner.getOrDefault(previousOwner, 0) - 1;
            if (remaining <= 0) vehiclesPerOwner.remove(previousOwner);
            else vehiclesPerOwner.put(previousOwner, remaining);
        }
        if (nextOwner != null) vehiclesPerOwner.put(nextOwner, vehiclesPerOwner.getOrDefault(nextOwner, 0) + 1);
    }

    private void copySection(ConfigurationSection source, ConfigurationSection target) {
        for (String key : source.getKeys(true)) {
            if (!source.isConfigurationSection(key)) target.set(key, source.get(key));
        }
    }

    private void recoverEmergencyFile(MariaDbVehicleStorage storage) throws Exception {
        File emergency = new File(Main.instance.getDataFolder(), "vehicleData-emergency.yml");
        if (!emergency.isFile()) return;
        YamlConfiguration recovery = new YamlConfiguration();
        try {
            recovery.load(emergency);
        } catch (IOException | InvalidConfigurationException exception) {
            Main.instance.getLogger().severe("The MariaDB emergency snapshot is invalid and was left untouched: "
                    + exception.getMessage());
            return;
        }
        ConfigurationSection vehicles = recovery.getConfigurationSection("vehicle");
        List<StoredVehicle> records = new ArrayList<>();
        if (vehicles != null) {
            for (String licensePlate : vehicles.getKeys(false)) {
                ConfigurationSection section = vehicles.getConfigurationSection(licensePlate);
                if (section != null) records.add(toStoredVehicle(licensePlate, section));
            }
        }
        storage.replaceAll(records);
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        Files.move(emergency.toPath(), new File(Main.instance.getDataFolder(),
                "vehicleData-emergency-recovered-" + timestamp + ".yml").toPath(), StandardCopyOption.REPLACE_EXISTING);
        Main.logInfo("Recovered " + records.size() + " vehicles from the emergency MariaDB snapshot.");
    }

    private void saveEmergencySnapshot() {
        saveSnapshot(new File(Main.instance.getDataFolder(), "vehicleData-emergency.yml"));
    }

    private void saveSnapshot(File target) {
        File temporary = new File(target.getParentFile(), target.getName() + ".tmp");
        YamlConfiguration recovery = new YamlConfiguration();
        for (Map.Entry<String, ConfigurationSection> entry : vehicleDataInMemory.entrySet()) {
            ConfigurationSection section = recovery.createSection("vehicle." + entry.getKey());
            copySection(entry.getValue(), section);
        }
        try {
            recovery.save(temporary);
            try {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            Main.instance.getLogger().severe("Could not save vehicle compatibility snapshot "
                    + target.getName() + ": " + exception.getMessage());
        }
    }


    /**
     * Whether the vehicleData.yml file is empty
     */
    public boolean isEmpty() {
        return vehicleDataInMemory.isEmpty();
    }

    /**
     * Get (basically) the whole file.
     */
    public Map<String, ConfigurationSection> getVehicles() {
        return new HashMap<>(vehicleDataInMemory);
    }

    /**
     * Get the durability of a vehicle item.
     * @param licensePlate Vehicle's license plate
     */
    public int getDamage(String licensePlate){
        return (int) get(licensePlate, Option.SKIN_DAMAGE);
    }

    /**
     * Get the durability of a vehicle item.
     * @param vehicle Vehicle
     */
    public int getDamage(Vehicle vehicle){
        return getDamage(vehicle.getLicensePlate());
    }

    /**
     * Get UUIDs of players which may sit in the vehicle
     * @param licensePlate Vehicle's license plate
     */
    public List<String> getMembers(String licensePlate){
        if (get(licensePlate, Option.MEMBERS) == null) return new ArrayList<>();
        return (List<String>) get(licensePlate, Option.MEMBERS);
    }

    /**
     * Get UUIDs of players which may steer the vehicle
     * @param licensePlate Vehicle's license plate
     */
    public List<String> getRiders(String licensePlate){
        if (get(licensePlate, Option.RIDERS) == null) return new ArrayList<>();
        return (List<String>) get(licensePlate, Option.RIDERS);
    }

    /**
     * Get data of the vehicle's trunk
     * @param licensePlate Vehicle's license plate
     * @return List of items in the trunk (as Strings)
     */
    public List<String> getTrunkData(String licensePlate){
        if (get(licensePlate, Option.TRUNK_DATA) == null) return new ArrayList<>();
        return (List<String>) get(licensePlate, Option.TRUNK_DATA);
    }

    /**
     * Get the type (enum) of the vehicle.
     * @param licensePlate Vehicle's license plate
     */
    public VehicleType getType(String licensePlate){
        try {
            return VehicleType.valueOf(get(licensePlate, Option.VEHICLE_TYPE).toString().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e){
            Main.logSevere("An error occurred while setting a vehicle's type. Using default (CAR)...");
            return VehicleType.CAR;
        }
    }

    /**
     * Check whether horn may be used in a vehicle
     * @param license Vehicle's license plate
     */
    public boolean isHornEnabled(String license){
        Boolean horn = (Boolean) get(license, Option.HORN_ENABLED);
        if (horn == null){
            setInitialHorn(license);
            horn = (Boolean) get(license, Option.HORN_ENABLED);
        }
        return horn;
    }

    /**
     * Check whether 'hornEnabled' data option is set (it might not be if player was using an older version of MTV before)
     * @param license Vehicle's license plate
     */
    public boolean isHornSet(String license){
        return get(license, Option.HORN_ENABLED) != null;
    }

    /**
     * Set the 'hornEnabled' data option default value.
     * @param license Vehicle's license plate
     */
    public void setInitialHorn(String license){
        boolean state = VehicleUtils.getHornByDamage(getDamage(license));
        set(license, Option.HORN_ENABLED, state);
    }


    /**
     * Get health of a vehicle
     * @param license Vehicle's license plate
     */
    public double getHealth(String license){
        Double health = (Double) get(license, Option.HEALTH);
        if (health == null) {
            setInitialHealth(license);
            health = (Double) get(license, Option.HEALTH);
        }
        return health;
    }

    /**
     * Check whether 'health' data option is set (it might not be if player was using an older version of MTV before)
     * @param license Vehicle's license plate
     */
    public boolean isHealthSet(String license){
        return get(license, Option.HEALTH) != null;
    }

    /**
     * Set the 'health' data option default value.
     * @param license Vehicle's license plate
     */
    public void setInitialHealth(String license){
        final int damage = getDamage(license);
        double state = VehicleUtils.getMaxHealthByDamage(damage);
        set(license, Option.HEALTH, state);
    }

    /**
     * Damage a vehicle.
     * @param license Vehicle's license plate
     * @param damage Amount of damage
     */
    public void damageVehicle(String license, double damage){
        final double health = getHealth(license) - damage;
        set(license, Option.HEALTH, Math.max(health, 0.0));
    }

    /**
     * Set health of a vehicle
     * @param license Vehicle's license plate
     * @param health New health
     */
    public void setHealth(String license, double health){
        set(license, Option.HEALTH, health);
    }

    /**
     * Get number of vehicles owned by a player
     * @param p Player
     */
    public int getNumberOfOwnedVehicles(Player p){
        return vehiclesPerOwner.getOrDefault(p.getUniqueId().toString(), 0);
    }

    /**
     * Options available in vehicle data file
     */
    public enum Option {
        NAME("name"),
        VEHICLE_TYPE("vehicleType"),
        SKIN_ITEM("skinItem"),
        SKIN_DAMAGE("skinDamage"),
        OWNER("owner"),
        RIDERS("riders"),
        MEMBERS("members"),
        /**
         * Can be found as 'benzineEnabled' in vehicleData.yml
         */
        FUEL_ENABLED("benzineEnabled"),
        /**
         * Can be found as 'fuel' in vehicleData.yml
         */
        FUEL("benzine"),
        /**
         * Can be found as 'benzineVerbruik' in vehicleData.yml
         */
        FUEL_USAGE("benzineVerbruik"),
        BRAKING_SPEED("brakingSpeed"),
        /**
         * Can be found as 'aftrekkenSpeed' in vehicleData.yml
         */
        FRICTION_SPEED("aftrekkenSpeed"),
        ACCELERATION_SPEED("acceleratieSpeed"),
        MAX_SPEED("maxSpeed"),
        MAX_SPEED_BACKWARDS("maxSpeedBackwards"),
        ROTATION_SPEED("rotateSpeed"),
        /**
         * Can be found as 'kofferbak' in vehicleData.yml
         */
        TRUNK_ENABLED("kofferbak"),
        /**
         * Can be found as 'kofferbakRows' in vehicleData.yml
         */
        TRUNK_ROWS("kofferbakRows"),
        /**
         * Can be found as 'kofferbakData' in vehicleData.yml
         */
        TRUNK_DATA("kofferbakData"),
        IS_OPEN("isOpen"),
        IS_GLOWING("isGlow"),
        HORN_ENABLED("hornEnabled"),
        HEALTH("health"),
        NBT_VALUE("nbtValue"),

        IS_PUBLIC("isPublic");

        final private String path;

        private Option(String path){
            this.path = path;
        }

        /**
         * Get string path of option
         * @return Path of option
         */
        public String getPath() {
            return path;
        }
    }
}
