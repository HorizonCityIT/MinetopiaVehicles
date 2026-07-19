package nl.mtvehicles.core.infrastructure.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nl.mtvehicles.core.infrastructure.dataconfig.StorageConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** MariaDB persistence. All methods are blocking and must run outside server ticks. */
public final class MariaDbVehicleStorage implements AutoCloseable {
    private static final int DATABASE_SCHEMA_VERSION = 1;
    private static final String MARIADB_DRIVER_CLASS = "org.mariadb.jdbc.Driver";

    private final HikariDataSource dataSource;
    private final String metadataTable;
    private final String vehiclesTable;

    public MariaDbVehicleStorage(StorageConfig config) throws SQLException {
        validateIdentifier(config.getDatabase(), "database");
        this.metadataTable = config.getTablePrefix() + "metadata";
        this.vehiclesTable = config.getTablePrefix() + "vehicles";

        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("MTVehicles-MariaDB");
        // Paper plugins use isolated classloaders. Relying only on JDBC's
        // service discovery can therefore hide the driver shaded in this JAR.
        hikari.setDriverClassName(MARIADB_DRIVER_CLASS);
        hikari.setJdbcUrl("jdbc:mariadb://" + config.getHost() + ':' + config.getPort() + '/'
                + config.getDatabase() + "?sslMode=" + config.getSslMode()
                + "&tcpKeepAlive=true&useServerPrepStmts=true");
        hikari.setUsername(config.getUsername());
        hikari.setPassword(config.getPassword());
        hikari.setMaximumPoolSize(config.getMaximumPoolSize());
        hikari.setMinimumIdle(config.getMinimumIdle());
        hikari.setConnectionTimeout(config.getConnectionTimeoutMs());
        hikari.setValidationTimeout(Math.min(3000L, config.getConnectionTimeoutMs()));
        hikari.setMaxLifetime(config.getMaxLifetimeMs());
        hikari.setAutoCommit(true);
        hikari.setInitializationFailTimeout(config.getConnectionTimeoutMs());
        hikari.addDataSourceProperty("cachePrepStmts", "true");
        hikari.addDataSourceProperty("prepStmtCacheSize", "250");
        hikari.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        this.dataSource = new HikariDataSource(hikari);
        try {
            initializeSchema();
        } catch (SQLException exception) {
            this.dataSource.close();
            throw exception;
        }
    }

    private void initializeSchema() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS `" + metadataTable + "` ("
                    + "`meta_key` VARCHAR(64) NOT NULL,"
                    + "`meta_value` VARCHAR(255) NOT NULL,"
                    + "`updated_at` TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),"
                    + "PRIMARY KEY (`meta_key`)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS `" + vehiclesTable + "` ("
                    + "`license_plate` VARCHAR(64) NOT NULL,"
                    + "`owner_uuid` VARCHAR(36) NULL,"
                    + "`vehicle_type` VARCHAR(32) NULL,"
                    + "`skin_item` VARCHAR(512) NULL,"
                    + "`skin_damage` INT NULL,"
                    + "`payload` LONGTEXT NOT NULL,"
                    + "`schema_version` SMALLINT UNSIGNED NOT NULL DEFAULT 1,"
                    + "`updated_at` TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),"
                    + "PRIMARY KEY (`license_plate`),"
                    + "KEY `idx_owner_uuid` (`owner_uuid`),"
                    + "KEY `idx_vehicle_type` (`vehicle_type`)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
        }
        verifySchemaVersion();
    }

    private void verifySchemaVersion() throws SQLException {
        String select = "SELECT `meta_value` FROM `" + metadataTable + "` WHERE `meta_key` = 'schema-version'";
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(select)) {
            if (result.next()) {
                int existing;
                try {
                    existing = Integer.parseInt(result.getString(1));
                } catch (NumberFormatException exception) {
                    throw new SQLException("Invalid MTVehicles database schema version", exception);
                }
                if (existing > DATABASE_SCHEMA_VERSION) {
                    throw new SQLException("Database schema " + existing + " is newer than supported schema "
                            + DATABASE_SCHEMA_VERSION);
                }
                return;
            }
        }
        String insert = "INSERT INTO `" + metadataTable + "` (`meta_key`, `meta_value`) VALUES ('schema-version', ?) "
                + "ON DUPLICATE KEY UPDATE `meta_value` = `meta_value`";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(insert)) {
            statement.setString(1, String.valueOf(DATABASE_SCHEMA_VERSION));
            statement.executeUpdate();
        }
    }

    public Map<String, StoredVehicle> loadAll() throws SQLException {
        Map<String, StoredVehicle> result = new LinkedHashMap<>();
        String sql = "SELECT `license_plate`, `owner_uuid`, `vehicle_type`, `skin_item`, `skin_damage`, `payload` "
                + "FROM `" + vehiclesTable + "`";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                Integer skinDamage = rows.getObject("skin_damage") == null ? null : rows.getInt("skin_damage");
                StoredVehicle vehicle = new StoredVehicle(
                        rows.getString("license_plate"), rows.getString("owner_uuid"),
                        rows.getString("vehicle_type"), rows.getString("skin_item"),
                        skinDamage, rows.getString("payload"));
                result.put(vehicle.licensePlate(), vehicle);
            }
        }
        return result;
    }

    public void writeBatch(Collection<StoredVehicle> upserts, Collection<String> deletes) throws SQLException {
        if (upserts.isEmpty() && deletes.isEmpty()) return;
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (!deletes.isEmpty()) {
                    String sql = "DELETE FROM `" + vehiclesTable + "` WHERE `license_plate` = ?";
                    try (PreparedStatement statement = connection.prepareStatement(sql)) {
                        for (String licensePlate : deletes) {
                            statement.setString(1, licensePlate);
                            statement.addBatch();
                        }
                        statement.executeBatch();
                    }
                }
                // Delete first so a just-released plate can safely be reused in
                // the same transaction, including old rows with different case.
                if (!upserts.isEmpty()) {
                    try (PreparedStatement statement = connection.prepareStatement(insertSql())) {
                        for (StoredVehicle vehicle : upserts) {
                            bindVehicle(statement, vehicle);
                            statement.addBatch();
                        }
                        statement.executeBatch();
                    }
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    /** Replaces the complete vehicle dataset from an authoritative recovery snapshot. */
    public void replaceAll(Collection<StoredVehicle> vehicles) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("DELETE FROM `" + vehiclesTable + "`");
                }
                if (!vehicles.isEmpty()) {
                    try (PreparedStatement statement = connection.prepareStatement(insertSql())) {
                        for (StoredVehicle vehicle : vehicles) {
                            bindVehicle(statement, vehicle);
                            statement.addBatch();
                        }
                        statement.executeBatch();
                    }
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private String insertSql() {
        String sql = "INSERT INTO `" + vehiclesTable + "` "
                + "(`license_plate`, `owner_uuid`, `vehicle_type`, `skin_item`, `skin_damage`, `payload`, `schema_version`) "
                + "VALUES (?, ?, ?, ?, ?, ?, 1)";
        sql += " ON DUPLICATE KEY UPDATE `owner_uuid` = VALUES(`owner_uuid`),"
                + "`vehicle_type` = VALUES(`vehicle_type`), `skin_item` = VALUES(`skin_item`),"
                + "`skin_damage` = VALUES(`skin_damage`), `payload` = VALUES(`payload`), `schema_version` = 1";
        return sql;
    }

    private void bindVehicle(PreparedStatement statement, StoredVehicle vehicle) throws SQLException {
        statement.setString(1, vehicle.licensePlate());
        statement.setString(2, vehicle.ownerUuid());
        statement.setString(3, vehicle.vehicleType());
        statement.setString(4, vehicle.skinItem());
        if (vehicle.skinDamage() == null) statement.setNull(5, java.sql.Types.INTEGER);
        else statement.setInt(5, vehicle.skinDamage());
        statement.setString(6, vehicle.payload());
    }

    private void validateIdentifier(String identifier, String label) throws SQLException {
        if (identifier == null || !identifier.matches("[A-Za-z0-9_]{1,64}")) {
            throw new SQLException("Invalid MariaDB " + label + " identifier");
        }
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
