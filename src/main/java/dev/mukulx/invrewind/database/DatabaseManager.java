/**
 * InvRewind - https://github.com/mukulx/invrewind
 * Copyright (C) 2026 Mukulx
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package dev.mukulx.invrewind.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.mukulx.invrewind.InvRewind;
import dev.mukulx.invrewind.config.ConfigManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Level;

public class DatabaseManager {

    private final InvRewind plugin;
    private final ConfigManager configManager;
    private HikariDataSource dataSource;
    private String actualDbType;

    public DatabaseManager(@NotNull InvRewind plugin, @NotNull ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public boolean initialize() {
        FileConfiguration config = configManager.getConfig();
        String dbType = config.getString("database.type", "yaml");
        actualDbType = dbType;

        if (dbType.equalsIgnoreCase("yaml")) {
            plugin.getLogger().info("Using YAML file-based storage");
            return initializeYaml();
        }

        try {
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setConnectionTestQuery("SELECT 1");

            if (dbType.equalsIgnoreCase("mysql")) {
                setupMySQL(hikariConfig, config);
                hikariConfig.setMaximumPoolSize(10);
                hikariConfig.setMinimumIdle(2);
                hikariConfig.setConnectionTimeout(30000);
                hikariConfig.setIdleTimeout(600000);
                hikariConfig.setMaxLifetime(1800000);
            } else {
                setupSQLite(hikariConfig, config);
            }

            dataSource = new HikariDataSource(hikariConfig);

            try (Connection testConn = dataSource.getConnection()) {
                if (testConn == null || testConn.isClosed()) {
                    throw new SQLException("Failed to establish database connection");
                }
            }

            createTables();

            plugin.getLogger().info("Database initialized successfully using " + dbType);
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize " + dbType + " database!", e);
            plugin.getLogger().warning("Falling back to YAML storage to prevent data loss");
            plugin.getLogger().warning("Please fix your database configuration and restart the server");
            
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
                dataSource = null;
            }
            
            actualDbType = "yaml";
            return initializeYaml();
        }
    }

    private boolean initializeYaml() {
        try {
            File backupsFolder = new File(plugin.getDataFolder(), 
                configManager.getConfig().getString("database.yaml.folder", "backups"));
            if (!backupsFolder.exists()) {
                backupsFolder.mkdirs();
            }
            plugin.getLogger().info("YAML backup storage initialized at: " + backupsFolder.getAbsolutePath());
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize YAML storage!", e);
            return false;
        }
    }

    private void setupSQLite(@NotNull HikariConfig config, @NotNull FileConfiguration pluginConfig) {
        String fileName = pluginConfig.getString("database.sqlite.file", "invrewind.db");
        File dbFile = new File(plugin.getDataFolder(), fileName);

        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
        config.setPoolName("InvRewind-SQLite");

        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(5000);
        config.setMaxLifetime(0);

        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous", "NORMAL");
        config.addDataSourceProperty("cache_size", "10000");
        config.addDataSourceProperty("temp_store", "MEMORY");
        config.addDataSourceProperty("mmap_size", "30000000000");
    }

    private void setupMySQL(@NotNull HikariConfig config, @NotNull FileConfiguration pluginConfig) {
        String host = pluginConfig.getString("database.mysql.host", "localhost");
        int port = pluginConfig.getInt("database.mysql.port", 3306);
        String database = pluginConfig.getString("database.mysql.database", "invrewind");
        String username = pluginConfig.getString("database.mysql.username", "root");
        String password = pluginConfig.getString("database.mysql.password", "password");

        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setPoolName("InvRewind-MySQL");

        config.setKeepaliveTime(300000);
        config.setConnectionTestQuery("SELECT 1");

        config.addDataSourceProperty("useSSL", false);
        config.addDataSourceProperty("autoReconnect", true);
        config.addDataSourceProperty("cachePrepStmts", true);
        config.addDataSourceProperty("prepStmtCacheSize", 250);
        config.addDataSourceProperty("prepStmtCacheSqlLimit", 2048);
        config.addDataSourceProperty("useServerPrepStmts", true);
        config.addDataSourceProperty("useLocalSessionState", true);
        config.addDataSourceProperty("rewriteBatchedStatements", true);
        config.addDataSourceProperty("cacheResultSetMetadata", true);
        config.addDataSourceProperty("cacheServerConfiguration", true);
        config.addDataSourceProperty("elideSetAutoCommits", true);
        config.addDataSourceProperty("maintainTimeStats", false);
        config.addDataSourceProperty("tcpKeepAlive", true);
    }

    private void createTables() throws SQLException {
        try (Connection conn = getConnection()) {
            FileConfiguration config = configManager.getConfig();
            String dbType = config.getString("database.type", "sqlite");

            String backupsTable;
            if (dbType.equalsIgnoreCase("mysql")) {
                backupsTable = """
                    CREATE TABLE IF NOT EXISTS invrewind_backups (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        player_uuid VARCHAR(36) NOT NULL,
                        player_name VARCHAR(16) NOT NULL,
                        backup_type VARCHAR(20) NOT NULL,
                        timestamp BIGINT NOT NULL,
                        world VARCHAR(255),
                        x DOUBLE,
                        y DOUBLE,
                        z DOUBLE,
                        yaw FLOAT,
                        pitch FLOAT,
                        health DOUBLE,
                        hunger INT,
                        xp_level INT,
                        xp_progress FLOAT,
                        inventory_data TEXT,
                        armor_data TEXT,
                        offhand_data TEXT,
                        enderchest_data TEXT,
                        INDEX idx_player_uuid (player_uuid),
                        INDEX idx_backup_type (backup_type),
                        INDEX idx_timestamp (timestamp)
                    )
                    """;
            } else {
                backupsTable = """
                    CREATE TABLE IF NOT EXISTS invrewind_backups (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        player_uuid TEXT NOT NULL,
                        player_name TEXT NOT NULL,
                        backup_type TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        world TEXT,
                        x REAL,
                        y REAL,
                        z REAL,
                        yaw REAL,
                        pitch REAL,
                        health REAL,
                        hunger INTEGER,
                        xp_level INTEGER,
                        xp_progress REAL,
                        inventory_data TEXT,
                        armor_data TEXT,
                        offhand_data TEXT,
                        enderchest_data TEXT
                    )
                    """;

                try (PreparedStatement stmt = conn.prepareStatement(backupsTable)) {
                    stmt.execute();
                }

                String[] indexes = {
                    "CREATE INDEX IF NOT EXISTS idx_player_uuid ON invrewind_backups(player_uuid)",
                    "CREATE INDEX IF NOT EXISTS idx_backup_type ON invrewind_backups(backup_type)",
                    "CREATE INDEX IF NOT EXISTS idx_timestamp ON invrewind_backups(timestamp)"
                };

                for (String index : indexes) {
                    try (PreparedStatement stmt = conn.prepareStatement(index)) {
                        stmt.execute();
                    }
                }

                plugin.getLogger().info("Database tables created successfully");
                return;
            }

            try (PreparedStatement stmt = conn.prepareStatement(backupsTable)) {
                stmt.execute();
            }

            plugin.getLogger().info("Database tables created successfully");
        }
    }

    @Nullable
    public Connection getConnection() {
        try {
            if (dataSource != null && !dataSource.isClosed()) {
                return dataSource.getConnection();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get database connection!", e);
        }
        return null;
    }

    public boolean isYamlMode() {
        return actualDbType != null && actualDbType.equalsIgnoreCase("yaml");
    }

    @NotNull
    public String getActualDatabaseType() {
        return actualDbType != null ? actualDbType : "yaml";
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Database connection closed");
        }
    }

    @NotNull
    public HikariDataSource getDataSource() {
        return dataSource;
    }
}
