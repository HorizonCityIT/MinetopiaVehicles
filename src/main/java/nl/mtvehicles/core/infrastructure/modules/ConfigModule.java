package nl.mtvehicles.core.infrastructure.modules;

import lombok.Getter;
import lombok.Setter;
import nl.mtvehicles.core.Main;
import nl.mtvehicles.core.infrastructure.dataconfig.*;
import nl.mtvehicles.core.infrastructure.models.MTVConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Module for managing configuration files
 */
public class ConfigModule {
    public static boolean storageReady;
    private static @Getter
    @Setter
    ConfigModule instance;

    /**
     * List of all configuration files.
     */
    public static List<MTVConfig> configList = new ArrayList<>();

    /**
     * SuperSecretSettings configuration file
     */
    public static SecretSettingsConfig secretSettings = new SecretSettingsConfig();
    /**
     * messages_xx.yml configuration files
     */
    public static MessagesConfig messagesConfig = new MessagesConfig();
    /** Persistent storage configuration file. */
    public static StorageConfig storageConfig = new StorageConfig();
    /**
     * VehicleData.yml configuration file
     */
    public static VehicleDataConfig vehicleDataConfig = new VehicleDataConfig();
    /**
     * Vehicles.yml configuration file
     */
    public static VehiclesConfig vehiclesConfig = new VehiclesConfig();
    /**
     * Default configuration file (config.yml)
     */
    public static DefaultConfig defaultConfig = new DefaultConfig();
    /** Siren sound sequences. */
    public static SirensConfig sirensConfig = new SirensConfig();

    public ConfigModule() {
        Main.instance.saveResource("credits.txt", true);

        configList.add(secretSettings);
        configList.add(messagesConfig);
        configList.add(storageConfig);
        configList.add(vehicleDataConfig);
        configList.add(vehiclesConfig);
        configList.add(defaultConfig);
        configList.add(sirensConfig);
        reloadConfigs();
        secretSettings.updateVersions(Main.configVersion, Main.messagesVersion);
        storageReady = vehicleDataConfig.initializeStorage(storageConfig);
    }

    /**
     * Reload all configuration files.
     */
    public static void reloadConfigs(){
        configList.forEach(MTVConfig::reload);
        if (!messagesConfig.setLanguageFile(secretSettings.getMessagesLanguage())){
            Main.instance.getLogger().severe("Messages.yml for your desired language could not be found. Disabling the plugin...");
            Main.disablePlugin();
        }
    }
}
