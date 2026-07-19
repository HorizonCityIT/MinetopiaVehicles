package nl.mtvehicles.core.infrastructure.dataconfig;

import nl.mtvehicles.core.Main;
import nl.mtvehicles.core.infrastructure.enums.ConfigType;
import nl.mtvehicles.core.infrastructure.models.MTVConfig;

import java.util.Locale;

/** Configuration for persistent, non-static plugin data. */
public final class StorageConfig extends MTVConfig {
    public StorageConfig() {
        super(ConfigType.STORAGE);
    }

    public StorageType getStorageType() {
        String configured = getConfiguration().getString("storage.type", "YAML");
        try {
            return StorageType.valueOf(configured.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            Main.logWarning("Unknown storage.type '" + configured + "'. Falling back to YAML.");
            return StorageType.YAML;
        }
    }

    public String getHost() {
        return getConfiguration().getString("storage.mariadb.host", "127.0.0.1").trim();
    }

    public int getPort() {
        return boundedInt("storage.mariadb.port", 3306, 1, 65535);
    }

    public String getDatabase() {
        return getConfiguration().getString("storage.mariadb.database", "mtvehicles").trim();
    }

    public String getUsername() {
        return getConfiguration().getString("storage.mariadb.username", "mtvehicles").trim();
    }

    public String getPassword() {
        return getConfiguration().getString("storage.mariadb.password", "");
    }

    public String getTablePrefix() {
        String prefix = getConfiguration().getString("storage.mariadb.tablePrefix", "mtv_").trim();
        if (!prefix.matches("[A-Za-z0-9_]{1,24}")) {
            Main.logWarning("Invalid MariaDB tablePrefix. Using 'mtv_'.");
            return "mtv_";
        }
        return prefix;
    }

    public String getSslMode() {
        String mode = getConfiguration().getString("storage.mariadb.sslMode", "DISABLE")
                .trim().replace('-', '_').toUpperCase(Locale.ROOT);
        if (!mode.equals("DISABLE") && !mode.equals("TRUST") && !mode.equals("VERIFY_CA") && !mode.equals("VERIFY_FULL")) {
            Main.logWarning("Invalid MariaDB sslMode. Using DISABLE.");
            return "disable";
        }
        return mode.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public int getMaximumPoolSize() {
        return boundedInt("storage.mariadb.pool.maximumPoolSize", 6, 2, 32);
    }

    public int getMinimumIdle() {
        return boundedInt("storage.mariadb.pool.minimumIdle", 1, 0, getMaximumPoolSize());
    }

    public long getConnectionTimeoutMs() {
        return boundedLong("storage.mariadb.pool.connectionTimeoutMs", 5000L, 250L, 30000L);
    }

    public long getMaxLifetimeMs() {
        return boundedLong("storage.mariadb.pool.maxLifetimeMs", 1800000L, 30000L, 3600000L);
    }

    public long getFlushIntervalTicks() {
        long seconds = boundedLong("storage.mariadb.flushIntervalSeconds", 5L, 1L, 300L);
        return seconds * 20L;
    }

    private int boundedInt(String path, int defaultValue, int min, int max) {
        int value = getConfiguration().getInt(path, defaultValue);
        return Math.max(min, Math.min(max, value));
    }

    private long boundedLong(String path, long defaultValue, long min, long max) {
        long value = getConfiguration().getLong(path, defaultValue);
        return Math.max(min, Math.min(max, value));
    }

    public enum StorageType {
        YAML,
        MARIADB
    }
}
