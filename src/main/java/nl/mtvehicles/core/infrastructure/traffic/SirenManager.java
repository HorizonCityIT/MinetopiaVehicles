package nl.mtvehicles.core.infrastructure.traffic;

import nl.mtvehicles.core.Main;
import nl.mtvehicles.core.infrastructure.modules.ConfigModule;
import nl.mtvehicles.core.infrastructure.utils.TextUtils;
import nl.mtvehicles.core.infrastructure.vehicle.Vehicle;
import nl.mtvehicles.core.infrastructure.vehicle.VehicleData;
import nl.mtvehicles.core.infrastructure.vehicle.VehicleUtils;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Handles configurable sirens for emergency-service vehicles. */
public class SirenManager implements Listener {
    private final Map<String, ActiveSiren> activeSirens = new HashMap<>();
    private BukkitTask task;
    private long currentTick;

    public void start() {
        task = Main.instance.getServer().getScheduler().runTaskTimer(Main.instance, this::tick, 1L, 1L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        activeSirens.clear();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!VehicleUtils.isInsideVehicle(player)) return;

        String license = VehicleUtils.getLicensePlate(player.getVehicle());
        if (license == null || !player.equals(VehicleUtils.getCurrentDriver(license))) return;

        Vehicle vehicle = VehicleUtils.getVehicle(license);
        if (!isEmergencyVehicle(vehicle)) return;

        event.setCancelled(true);
        if (activeSirens.remove(license) != null) {
            player.sendMessage(TextUtils.colorize("&7Sirena &cdisattivata&7."));
            return;
        }

        String type = getSirenType(vehicle);
        activeSirens.put(license, new ActiveSiren(type));
        player.sendMessage(TextUtils.colorize("&7Sirena &aattivata &7(&f" + type + "&7)."));
    }

    private void tick() {
        currentTick++;
        Iterator<Map.Entry<String, ActiveSiren>> iterator = activeSirens.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, ActiveSiren> entry = iterator.next();
            String license = entry.getKey();
            ActiveSiren state = entry.getValue();
            Player driver = VehicleUtils.getCurrentDriver(license);
            ArmorStand mainStand = VehicleData.autostand.get("MTVEHICLES_MAIN_" + license);
            if (driver == null || mainStand == null || !mainStand.isValid()) {
                iterator.remove();
                continue;
            }

            int interval = ConfigModule.sirensConfig.getIntervalTicks(state.type);
            if (currentTick < state.nextToneTick) continue;

            List<Map<?, ?>> tones = ConfigModule.sirensConfig.getTones(state.type);
            if (tones.isEmpty()) {
                iterator.remove();
                Main.logWarning("Siren profile " + state.type + " has no valid tones; siren disabled for " + license + ".");
                continue;
            }

            Map<?, ?> tone = tones.get(state.toneIndex % tones.size());
            Object soundValue = tone.get("sound");
            if (soundValue != null) {
                float pitch = number(tone.get("pitch"), 1.0F);
                mainStand.getWorld().playSound(mainStand, soundValue.toString(),
                        ConfigModule.sirensConfig.getVolume(state.type), pitch);
            }
            state.toneIndex = (state.toneIndex + 1) % tones.size();
            state.nextToneTick = currentTick + interval;
        }
    }

    public static boolean isEmergencyVehicle(Vehicle vehicle) {
        if (vehicle == null || vehicle.getVehicleData() == null) return false;
        Object flag = vehicle.getVehicleData().get("fdo");
        return flag instanceof Boolean ? (Boolean) flag : flag != null && Boolean.parseBoolean(flag.toString());
    }

    public static String getSirenType(Vehicle vehicle) {
        Object configured = vehicle.getVehicleData().get("sirenType");
        return ConfigModule.sirensConfig.normalize(configured == null ? "POLICE" : configured.toString());
    }

    private static float number(Object value, float fallback) {
        return value instanceof Number ? ((Number) value).floatValue() : fallback;
    }

    private static final class ActiveSiren {
        private final String type;
        private int toneIndex;
        private long nextToneTick;

        private ActiveSiren(String type) {
            this.type = type;
        }
    }
}
