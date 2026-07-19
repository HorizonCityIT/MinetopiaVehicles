package nl.mtvehicles.core.infrastructure.utils;

import nl.mtvehicles.core.infrastructure.dataconfig.DefaultConfig;
import nl.mtvehicles.core.infrastructure.dataconfig.VehicleDataConfig;
import nl.mtvehicles.core.infrastructure.enums.Message;
import nl.mtvehicles.core.infrastructure.modules.ConfigModule;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.HashMap;

/**
 * Methods for BossBars
 */
public class BossBarUtils {
    /**
     * Fuel bossbar
     */
    public static HashMap<String, BossBar> Fuelbar = new HashMap<>();
    private static final HashMap<String, Integer> lastFuelPercent = new HashMap<>();

    /**
     * Set bossbar's fuel amount
     * @param counter Fuel amount
     * @param licensePlate Vehicle's license plate (this vehicle's fuel is displayed)
     */
    public static void setBossBarValue(double counter, String licensePlate) {
        if ((boolean) ConfigModule.defaultConfig.get(DefaultConfig.Option.FUEL_ENABLED) && (boolean) ConfigModule.vehicleDataConfig.get(licensePlate, VehicleDataConfig.Option.FUEL_ENABLED)) {

            BossBar bar = Fuelbar.get(licensePlate);
            if (bar == null) return;

            double safeCounter = Math.max(0.0D, Math.min(counter, 1.0D));
            int percent = (int) Math.round(safeCounter * 100.0D);
            if (lastFuelPercent.getOrDefault(licensePlate, -1) == percent) return;

            lastFuelPercent.put(licensePlate, percent);
            bar.setProgress(safeCounter);
            bar.setTitle(percent + "% " + TextUtils.colorize(ConfigModule.messagesConfig.getMessage(Message.BOSSBAR_FUEL)));
            if (percent < 30) bar.setColor(BarColor.RED);
            else if (percent < 60) bar.setColor(BarColor.YELLOW);
            else bar.setColor(BarColor.GREEN);
        }
    }

    /**
     * Remove fuel bossbar from player
     * @param player Player
     * @param licensePlate Vehicle's license plate (this vehicle's fuel is displayed)
     */
    public static void removeBossBar(Player player, String licensePlate) {
        if ((boolean) ConfigModule.defaultConfig.get(DefaultConfig.Option.FUEL_ENABLED) && (boolean) ConfigModule.vehicleDataConfig.get(licensePlate, VehicleDataConfig.Option.FUEL_ENABLED)) {
            BossBar bar = Fuelbar.get(licensePlate);
            if (bar == null) return;
            bar.removePlayer(player);
            if (bar.getPlayers().isEmpty()) {
                Fuelbar.remove(licensePlate);
                lastFuelPercent.remove(licensePlate);
            }
        }
    }

    /**
     * Show fuel bossbar for a player
     * @param player Player
     * @param licensePlate Vehicle's license plate (this vehicle's fuel is displayed)
     */
    public static void addBossBar(Player player, String licensePlate) {
        if ((boolean) ConfigModule.defaultConfig.get(DefaultConfig.Option.FUEL_ENABLED) && (boolean) ConfigModule.vehicleDataConfig.get(licensePlate, VehicleDataConfig.Option.FUEL_ENABLED)) {
            double fuel = (double) ConfigModule.vehicleDataConfig.get(licensePlate, VehicleDataConfig.Option.FUEL);
            String fuelString = String.valueOf(fuel);
            BossBar bar = Bukkit.createBossBar(Math.round(Double.parseDouble(fuelString)) + "% " + TextUtils.colorize(ConfigModule.messagesConfig.getMessage(Message.BOSSBAR_FUEL)), BarColor.GREEN, BarStyle.SOLID);
            Fuelbar.put(licensePlate, bar);
            int percent = (int) Math.round(Math.max(0.0D, Math.min(fuel, 100.0D)));
            lastFuelPercent.put(licensePlate, percent);
            if (percent < 30) bar.setColor(BarColor.RED);
            else if (percent < 60) bar.setColor(BarColor.YELLOW);
            else bar.setColor(BarColor.GREEN);
            bar.setProgress(percent / 100.0D);
            bar.addPlayer(player);
        }
    }
}
