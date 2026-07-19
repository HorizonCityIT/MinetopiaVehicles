package nl.mtvehicles.core.infrastructure.models;

import nl.mtvehicles.core.Main;
import nl.mtvehicles.core.infrastructure.enums.ConfigType;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;

/**
 * Abstract class for plugin's configuration files
 */
public abstract class MTVConfig {
    /**
     * Type of the configuration file
     */
    final protected ConfigType configType;
    /**
     * Configuration file
     */
    protected FileConfiguration config;
    private File configFile = null;
    private String fileName;

    /**
     * Basic setter
     * @param configType Type of the config
     */
    public MTVConfig(ConfigType configType){
        this.configType = configType;
        if (!configType.isMessages()) this.fileName = configType.getFileName();
    }

    /**
     * Reload configuration file (e.g. if you've just edited it in a text editor)
     */
    public void reload() {
        if (configFile == null) {
            setConfigFile(new File(Main.instance.getDataFolder(), fileName));
        }
        if (!configFile.exists()) this.saveDefaultConfig();

        YamlConfiguration loaded = loadYaml(configFile);
        if (configType != ConfigType.VEHICLE_DATA) {
            YamlConfiguration defaults = loadDefaults(fileName);
            int addedSettings = mergeMissingDefaults(loaded, defaults);
            loaded.setDefaults(defaults);
            if (addedSettings > 0) {
                try {
                    saveAtomically(loaded, configFile);
                    Main.logInfo("Updated " + fileName + " with " + addedSettings + " new setting(s). Existing values were preserved.");
                } catch (IOException exception) {
                    throw new IllegalStateException("Could not update " + fileName + " atomically", exception);
                }
            }
        }
        config = loaded;
    }


    /**
     * Get the file configuration
     * @deprecated New alternative methods have been created in the 'nl.mtvehicles.core.infrastructure.dataconfig' package. Otherwise, {@link #getConfiguration()} should be used instead.
     *
     * @return Config as FileConfiguration
     * @see #getConfiguration()
     */
    @Deprecated
    public FileConfiguration getConfig() {
        if (config == null) {
            reload();
        }
        return config;
    }

    /**
     * Get the file configuration (new method, protected - should be only used in config classes)
     *
     * @return Config as FileConfiguration
     */
    protected FileConfiguration getConfiguration() {
        if (config == null) {
            reload();
        }
        return config;
    }

    /**
     * Save the newly assigned values to the configuration file
     * @return True if saving was successful
     */
    public boolean save() {
        if (config == null || configFile == null) {
            return false;
        }
        try {
            saveAtomically(getConfiguration(), configFile);
        } catch (IOException ex) {
            Main.instance.getLogger().log(Level.SEVERE, "Could not save config to " + configFile, ex);
            return false;
        }
        this.reload();
        return true;
    }

    /**
     * Save the default configuration file
     */
    public void saveDefaultConfig() {
        if (configFile == null) {
            configFile = new File(Main.instance.getDataFolder(), fileName);
        }
        if (!configFile.exists()) {
            Main.instance.saveResource(fileName, false);
        }
    }

    /**
     * Set the name of the configuration file (e.g. 'messages/messages_en.yml')
     * @param fileName Name of the configuration file
     */
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    /**
     * Set the configuration file
     * @param configFile Configuration file
     */
    public void setConfigFile(File configFile){
        this.configFile = configFile;
    }

    /** Ensure an auxiliary bundled YAML file contains every currently shipped key. */
    protected final void synchronizeDefaultFile(String resourcePath) {
        File target = new File(Main.instance.getDataFolder(), resourcePath);
        if (!target.exists()) {
            Main.instance.saveResource(resourcePath, false);
            return;
        }

        YamlConfiguration loaded = loadYaml(target);
        YamlConfiguration defaults = loadDefaults(resourcePath);
        int addedSettings = mergeMissingDefaults(loaded, defaults);
        if (addedSettings == 0) return;

        try {
            saveAtomically(loaded, target);
            Main.logInfo("Updated " + resourcePath + " with " + addedSettings
                    + " new setting(s). Existing values were preserved.");
        } catch (IOException exception) {
            throw new IllegalStateException("Could not update " + resourcePath + " atomically", exception);
        }
    }

    private static YamlConfiguration loadYaml(File source) {
        YamlConfiguration loaded = new YamlConfiguration();
        loaded.options().parseComments(true);
        try {
            loaded.load(source);
            return loaded;
        } catch (IOException | InvalidConfigurationException exception) {
            throw new IllegalStateException("Invalid YAML configuration " + source.getAbsolutePath(), exception);
        }
    }

    private static YamlConfiguration loadDefaults(String resourcePath) {
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.options().parseComments(true);
        try (Reader reader = new InputStreamReader(requireResource(resourcePath), StandardCharsets.UTF_8)) {
            defaults.load(reader);
            return defaults;
        } catch (IOException | InvalidConfigurationException exception) {
            throw new IllegalStateException("Invalid bundled YAML configuration " + resourcePath, exception);
        }
    }

    private static java.io.InputStream requireResource(String resourcePath) {
        java.io.InputStream resource = Main.instance.getResource(resourcePath);
        if (resource == null) throw new IllegalStateException("Missing bundled resource " + resourcePath);
        return resource;
    }

    /** Add only missing leaf values; configured server values always win. */
    private static int mergeMissingDefaults(YamlConfiguration target, YamlConfiguration defaults) {
        int addedSettings = 0;
        for (String path : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(path) || target.contains(path, true)) continue;
            target.set(path, defaults.get(path));
            addedSettings++;
        }
        if (addedSettings == 0) return 0;

        for (String path : defaults.getKeys(true)) {
            if (!target.contains(path, true)) continue;
            List<String> comments = defaults.getComments(path);
            if (target.getComments(path).isEmpty() && !comments.isEmpty()) target.setComments(path, comments);
            List<String> inlineComments = defaults.getInlineComments(path);
            if (target.getInlineComments(path).isEmpty() && !inlineComments.isEmpty()) {
                target.setInlineComments(path, inlineComments);
            }
        }
        if (target.options().getHeader().isEmpty()) target.options().setHeader(defaults.options().getHeader());
        if (target.options().getFooter().isEmpty()) target.options().setFooter(defaults.options().getFooter());
        return addedSettings;
    }

    private static void saveAtomically(FileConfiguration configuration, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null) Files.createDirectories(parent.toPath());
        Path temporary = Files.createTempFile(parent == null ? Path.of(".") : parent.toPath(),
                target.getName() + '.', ".tmp");
        try {
            configuration.save(temporary.toFile());
            try {
                Files.move(temporary, target.toPath(), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
