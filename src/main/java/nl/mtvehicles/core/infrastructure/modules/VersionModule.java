package nl.mtvehicles.core.infrastructure.modules;

import nl.mtvehicles.core.Main;
import org.bukkit.Bukkit;

import java.util.Locale;

/** Runtime version gate for the HorizonCity Paper 1.21.11 build. */
public final class VersionModule {
    public static final String SUPPORTED_MINECRAFT_VERSION = "1.21.11";

    public static boolean isPreRelease;
    public static boolean isDevRelease;
    private static String pluginVersionString;
    private final String minecraftVersion;

    public VersionModule() {
        pluginVersionString = Main.instance.getPluginMeta().getVersion();
        String normalizedVersion = pluginVersionString.toLowerCase(Locale.ROOT);
        isPreRelease = normalizedVersion.contains("pre")
                || normalizedVersion.contains("rc")
                || normalizedVersion.contains("dev");
        isDevRelease = normalizedVersion.contains("dev");
        minecraftVersion = Bukkit.getMinecraftVersion();
    }

    public static String getPluginVersion() {
        return pluginVersionString;
    }

    public boolean isSupportedVersion() {
        if (SUPPORTED_MINECRAFT_VERSION.equals(minecraftVersion)) return true;

        Main.logSevere("MTVehicles HorizonCity supports only Minecraft "
                + SUPPORTED_MINECRAFT_VERSION + "; detected " + minecraftVersion + '.');
        Main.logSevere("The plugin was stopped before registering tasks or listeners.");
        return false;
    }
}
