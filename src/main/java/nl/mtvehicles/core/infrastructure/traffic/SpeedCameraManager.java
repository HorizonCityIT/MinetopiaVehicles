package nl.mtvehicles.core.infrastructure.traffic;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import nl.mtvehicles.core.Main;
import nl.mtvehicles.core.events.VehicleSpeedCameraEvent;
import nl.mtvehicles.core.infrastructure.utils.TextUtils;
import nl.mtvehicles.core.infrastructure.vehicle.VehicleData;
import nl.mtvehicles.core.infrastructure.vehicle.VehicleUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** Static speed cameras and player-operated dynamic speed guns. */
public class SpeedCameraManager {
    private static final int CELL_SIZE = 64;
    private static final long VIOLATION_COOLDOWN_MS = 10_000L;

    private final File dataFile = new File(Main.instance.getDataFolder(), "speedcameras.yml");
    private final Map<String, SpeedCamera> cameras = new LinkedHashMap<>();
    private final Map<String, Map<Long, List<SpeedCamera>>> cameraIndex = new HashMap<>();
    private final Map<UUID, Double> dynamicUsers = new HashMap<>();
    private final Map<String, Long> lastViolations = new HashMap<>();
    private BukkitTask task;

    public void start() {
        load();
        task = Bukkit.getScheduler().runTaskTimer(Main.instance, this::tick, 10L, 10L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        save();
        dynamicUsers.clear();
        lastViolations.clear();
    }

    public void reload() {
        load();
        lastViolations.clear();
    }

    public SpeedCamera addCamera(Location location, double limitKmh, double radius) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        SpeedCamera camera = new SpeedCamera(id, location.getWorld().getName(), location.getX(),
                location.getY(), location.getZ(), limitKmh, radius);
        cameras.put(id, camera);
        rebuildIndex();
        save();
        return camera;
    }

    public SpeedCamera removeNearest(Location location, double maxDistance) {
        SpeedCamera nearest = cameras.values().stream()
                .filter(camera -> camera.world.equals(location.getWorld().getName()))
                .filter(camera -> camera.distanceSquared(location) <= maxDistance * maxDistance)
                .min(Comparator.comparingDouble(camera -> camera.distanceSquared(location)))
                .orElse(null);
        if (nearest != null) {
            cameras.remove(nearest.id);
            rebuildIndex();
            save();
        }
        return nearest;
    }

    public boolean removeById(String id) {
        SpeedCamera removed = cameras.remove(id.toLowerCase(Locale.ROOT));
        if (removed == null) return false;
        rebuildIndex();
        save();
        return true;
    }

    public Collection<SpeedCamera> getCameras() {
        return Collections.unmodifiableCollection(new ArrayList<>(cameras.values()));
    }

    /** Returns true when dynamic mode has been enabled, false when it has been disabled. */
    public boolean toggleDynamic(Player player, double limitKmh) {
        if (dynamicUsers.remove(player.getUniqueId()) != null) return false;
        dynamicUsers.put(player.getUniqueId(), limitKmh);
        return true;
    }

    public void removeDynamicUser(Player player) {
        dynamicUsers.remove(player.getUniqueId());
    }

    private void tick() {
        checkStaticCameras();
        updateDynamicSpeedGuns();
    }

    private void checkStaticCameras() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, ArmorStand> entry : VehicleData.autostand2.entrySet()) {
            String license = entry.getKey();
            Player driver = VehicleUtils.getCurrentDriver(license);
            ArmorStand mainStand = VehicleData.autostand.get("MTVEHICLES_MAIN_" + license);
            if (driver == null || mainStand == null || !mainStand.isValid()) continue;

            double speed = getSpeedKmh(license);
            if (speed <= 0.0D) continue;
            Location vehicleLocation = mainStand.getLocation();
            for (SpeedCamera camera : nearbyCameras(vehicleLocation)) {
                if (speed <= camera.limitKmh || camera.distanceSquared(vehicleLocation) > camera.radius * camera.radius) continue;

                String violationKey = camera.id + ':' + license;
                if (now - lastViolations.getOrDefault(violationKey, 0L) < VIOLATION_COOLDOWN_MS) continue;
                lastViolations.put(violationKey, now);

                VehicleSpeedCameraEvent event = new VehicleSpeedCameraEvent(camera.id, license, speed,
                        camera.limitKmh, camera.toLocation());
                event.setPlayer(driver);
                event.call();
                driver.sendMessage(TextUtils.colorize(String.format(Locale.US,
                        "&c[Autovelox] &f%.1f km/h &7(limite %.1f) &8- &7targa %s", speed, camera.limitKmh, license)));
            }
        }
    }

    private void updateDynamicSpeedGuns() {
        dynamicUsers.entrySet().removeIf(entry -> Bukkit.getPlayer(entry.getKey()) == null);
        for (Map.Entry<UUID, Double> entry : dynamicUsers.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) continue;

            RayTraceResult result = player.getWorld().rayTraceEntities(player.getEyeLocation(),
                    player.getEyeLocation().getDirection(), 80.0D, 1.5D, VehicleUtils::isVehicle);
            Entity target = result == null ? null : result.getHitEntity();
            String license = target == null ? null : VehicleUtils.getLicensePlate(target);
            if (license == null) {
                sendActionBar(player, "&7Telelaser: guarda un veicolo entro 80 blocchi");
                continue;
            }

            double speed = getSpeedKmh(license);
            double limit = entry.getValue();
            String color = speed > limit ? "&c" : "&a";
            sendActionBar(player, String.format(Locale.US,
                    "&7Targa &f%s &8| %s%.1f km/h &8| &7Limite %.1f", license, color, speed, limit));
        }
    }

    private List<SpeedCamera> nearbyCameras(Location location) {
        Map<Long, List<SpeedCamera>> worldIndex = cameraIndex.get(location.getWorld().getName());
        if (worldIndex == null) return Collections.emptyList();
        int cellX = Math.floorDiv(location.getBlockX(), CELL_SIZE);
        int cellZ = Math.floorDiv(location.getBlockZ(), CELL_SIZE);
        List<SpeedCamera> result = new ArrayList<>();
        for (int x = cellX - 1; x <= cellX + 1; x++) {
            for (int z = cellZ - 1; z <= cellZ + 1; z++) {
                result.addAll(worldIndex.getOrDefault(cellKey(x, z), Collections.emptyList()));
            }
        }
        return result;
    }

    private void rebuildIndex() {
        cameraIndex.clear();
        for (SpeedCamera camera : cameras.values()) {
            int cellX = Math.floorDiv((int) Math.floor(camera.x), CELL_SIZE);
            int cellZ = Math.floorDiv((int) Math.floor(camera.z), CELL_SIZE);
            cameraIndex.computeIfAbsent(camera.world, ignored -> new HashMap<>())
                    .computeIfAbsent(cellKey(cellX, cellZ), ignored -> new ArrayList<>())
                    .add(camera);
        }
    }

    private static long cellKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    public static double getSpeedKmh(String license) {
        return Math.abs(VehicleData.speed.getOrDefault(license, 0.0D) * 20.0D * 3.6D);
    }

    private static void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacyText(TextUtils.colorize(message)));
    }

    private void load() {
        cameras.clear();
        if (!dataFile.exists()) {
            try {
                File parent = dataFile.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                dataFile.createNewFile();
            } catch (IOException exception) {
                Main.instance.getLogger().log(Level.SEVERE, "Could not create speedcameras.yml", exception);
                return;
            }
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection section = yaml.getConfigurationSection("cameras");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                String path = "cameras." + id + '.';
                String world = yaml.getString(path + "world");
                if (world == null) continue;
                double radius = Math.max(1.0D, Math.min(64.0D, yaml.getDouble(path + "radius", 6.0D)));
                double limit = Math.max(1.0D, yaml.getDouble(path + "limitKmh", 50.0D));
                cameras.put(id.toLowerCase(Locale.ROOT), new SpeedCamera(id.toLowerCase(Locale.ROOT), world,
                        yaml.getDouble(path + "x"), yaml.getDouble(path + "y"), yaml.getDouble(path + "z"), limit, radius));
            }
        }
        rebuildIndex();
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (SpeedCamera camera : cameras.values()) {
            String path = "cameras." + camera.id + '.';
            yaml.set(path + "world", camera.world);
            yaml.set(path + "x", camera.x);
            yaml.set(path + "y", camera.y);
            yaml.set(path + "z", camera.z);
            yaml.set(path + "limitKmh", camera.limitKmh);
            yaml.set(path + "radius", camera.radius);
        }
        try {
            yaml.save(dataFile);
        } catch (IOException exception) {
            Main.instance.getLogger().log(Level.SEVERE, "Could not save speedcameras.yml", exception);
        }
    }

    public static final class SpeedCamera {
        private final String id;
        private final String world;
        private final double x;
        private final double y;
        private final double z;
        private final double limitKmh;
        private final double radius;

        private SpeedCamera(String id, String world, double x, double y, double z, double limitKmh, double radius) {
            this.id = id;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.limitKmh = limitKmh;
            this.radius = radius;
        }

        public String getId() { return id; }
        public String getWorld() { return world; }
        public double getX() { return x; }
        public double getY() { return y; }
        public double getZ() { return z; }
        public double getLimitKmh() { return limitKmh; }
        public double getRadius() { return radius; }

        private double distanceSquared(Location location) {
            double dx = x - location.getX();
            double dy = y - location.getY();
            double dz = z - location.getZ();
            return dx * dx + dy * dy + dz * dz;
        }

        private Location toLocation() {
            World bukkitWorld = Bukkit.getWorld(world);
            return bukkitWorld == null ? new Location(Bukkit.getWorlds().get(0), x, y, z)
                    : new Location(bukkitWorld, x, y, z);
        }
    }
}
