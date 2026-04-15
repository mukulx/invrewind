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
package dev.mukulx.invrewind.managers;

import dev.mukulx.invrewind.InvRewind;
import dev.mukulx.invrewind.config.ConfigManager;
import dev.mukulx.invrewind.database.DatabaseManager;
import dev.mukulx.invrewind.database.YamlDatabaseManager;
import dev.mukulx.invrewind.model.BackupData;
import dev.mukulx.invrewind.util.ItemSerializer;
import dev.mukulx.invrewind.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class MigrationManager {

    private final InvRewind plugin;
    private final DatabaseManager databaseManager;
    private final ConfigManager configManager;
    private final File exportsFolder;
    private final File importsFolder;

    public MigrationManager(@NotNull InvRewind plugin, @NotNull DatabaseManager databaseManager,
                           @NotNull ConfigManager configManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.configManager = configManager;
        this.exportsFolder = new File(plugin.getDataFolder(), "exports");
        this.importsFolder = new File(plugin.getDataFolder(), "imports");
        
        createFolderStructure();
    }

    private void createFolderStructure() {
        new File(exportsFolder, "yaml").mkdirs();
        new File(exportsFolder, "sqlite").mkdirs();
        new File(exportsFolder, "mysql").mkdirs();
        new File(importsFolder, "yaml").mkdirs();
        new File(importsFolder, "sqlite").mkdirs();
        new File(importsFolder, "mysql").mkdirs();
    }

    @NotNull
    public CompletableFuture<String> exportData(@NotNull String targetType) {
        CompletableFuture<String> future = new CompletableFuture<>();
        
        SchedulerUtil.runTaskAsynchronously(plugin, () -> {
            try {
                String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new java.util.Date());
                String result;
                
                switch (targetType.toLowerCase()) {
                    case "yaml" -> result = exportToYaml(timestamp);
                    case "sqlite" -> result = exportToSqlite(timestamp);
                    case "mysql" -> result = exportToMysql(timestamp);
                    default -> {
                        future.complete("§cInvalid export type: " + targetType);
                        return;
                    }
                }
                
                future.complete(result);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Export failed", e);
                future.complete("§cExport failed: " + e.getMessage());
            }
        });
        
        return future;
    }

    private String exportToYaml(String timestamp) throws Exception {
        File exportFolder = new File(exportsFolder, "yaml/export_" + timestamp);
        exportFolder.mkdirs();
        
        List<BackupData> allBackups = getAllBackupsFromCurrentDb();
        
        if (allBackups.isEmpty()) {
            return "§cno backups found to export";
        }
        
        YamlDatabaseManager tempYaml = new YamlDatabaseManager(plugin);
        int count = 0;
        
        for (BackupData backup : allBackups) {
            File playerFolder = new File(exportFolder, backup.getBackupType().getKey() + "/" + backup.getPlayerName());
            playerFolder.mkdirs();
            
            File backupFile = new File(playerFolder, "backup_" + backup.getId() + ".yml");
            YamlConfiguration yaml = new YamlConfiguration();
            
            yaml.set("id", backup.getId());
            yaml.set("player-uuid", backup.getPlayerUuid().toString());
            yaml.set("player-name", backup.getPlayerName());
            yaml.set("backup-type", backup.getBackupType().getKey());
            yaml.set("timestamp", backup.getTimestamp());
            
            if (backup.getLocation() != null) {
                Location loc = backup.getLocation();
                yaml.set("location.world", loc.getWorld().getName());
                yaml.set("location.x", loc.getX());
                yaml.set("location.y", loc.getY());
                yaml.set("location.z", loc.getZ());
                yaml.set("location.yaw", loc.getYaw());
                yaml.set("location.pitch", loc.getPitch());
            }
            
            yaml.set("health", backup.getHealth());
            yaml.set("hunger", backup.getHunger());
            yaml.set("xp-level", backup.getXpLevel());
            yaml.set("xp-progress", backup.getXpProgress());
            
            if (backup.getInventory() != null) {
                yaml.set("inventory", Arrays.asList(backup.getInventory()));
            }
            if (backup.getArmor() != null) {
                yaml.set("armor", Arrays.asList(backup.getArmor()));
            }
            if (backup.getOffhand() != null) {
                yaml.set("offhand", backup.getOffhand());
            }
            if (backup.getEnderchest() != null) {
                yaml.set("enderchest", Arrays.asList(backup.getEnderchest()));
            }
            
            yaml.save(backupFile);
            count++;
            
            if (count % 100 == 0) {
                plugin.getLogger().info("Exported " + count + "/" + allBackups.size() + " backups...");
            }
        }
        
        return "§aexported " + count + " backups to: exports/yaml/export_" + timestamp;
    }

    private String exportToSqlite(String timestamp) throws Exception {
        File exportFile = new File(exportsFolder, "sqlite/export_" + timestamp + ".db");
        
        List<BackupData> allBackups = getAllBackupsFromCurrentDb();
        
        if (allBackups.isEmpty()) {
            return "§cno backups found to export";
        }
        
        String url = "jdbc:sqlite:" + exportFile.getAbsolutePath();
        
        try (Connection conn = DriverManager.getConnection(url)) {
            createSqliteTables(conn);
            
            String sql = """
                INSERT INTO invrewind_backups
                (id, player_uuid, player_name, backup_type, timestamp, world, x, y, z, yaw, pitch,
                 health, hunger, xp_level, xp_progress, inventory_data, armor_data, offhand_data, enderchest_data)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                int count = 0;
                
                for (BackupData backup : allBackups) {
                    stmt.setInt(1, backup.getId());
                    stmt.setString(2, backup.getPlayerUuid().toString());
                    stmt.setString(3, backup.getPlayerName());
                    stmt.setString(4, backup.getBackupType().getKey());
                    stmt.setLong(5, backup.getTimestamp());
                    
                    if (backup.getLocation() != null) {
                        Location loc = backup.getLocation();
                        stmt.setString(6, loc.getWorld().getName());
                        stmt.setDouble(7, loc.getX());
                        stmt.setDouble(8, loc.getY());
                        stmt.setDouble(9, loc.getZ());
                        stmt.setFloat(10, loc.getYaw());
                        stmt.setFloat(11, loc.getPitch());
                    } else {
                        stmt.setNull(6, Types.VARCHAR);
                        stmt.setNull(7, Types.DOUBLE);
                        stmt.setNull(8, Types.DOUBLE);
                        stmt.setNull(9, Types.DOUBLE);
                        stmt.setNull(10, Types.FLOAT);
                        stmt.setNull(11, Types.FLOAT);
                    }
                    
                    stmt.setDouble(12, backup.getHealth());
                    stmt.setInt(13, backup.getHunger());
                    stmt.setInt(14, backup.getXpLevel());
                    stmt.setFloat(15, backup.getXpProgress());
                    stmt.setString(16, backup.getInventory() != null ? ItemSerializer.serializeItems(backup.getInventory()) : null);
                    stmt.setString(17, backup.getArmor() != null ? ItemSerializer.serializeItems(backup.getArmor()) : null);
                    stmt.setString(18, backup.getOffhand() != null ? ItemSerializer.serializeItem(backup.getOffhand()) : null);
                    stmt.setString(19, backup.getEnderchest() != null ? ItemSerializer.serializeItems(backup.getEnderchest()) : null);
                    
                    stmt.executeUpdate();
                    count++;
                    
                    if (count % 100 == 0) {
                        plugin.getLogger().info("Exported " + count + "/" + allBackups.size() + " backups...");
                    }
                }
                
                return "§aexported " + count + " backups to: exports/sqlite/export_" + timestamp + ".db";
            }
        }
    }

    private String exportToMysql(String timestamp) throws Exception {
        File exportFile = new File(exportsFolder, "mysql/export_" + timestamp + ".sql");
        
        List<BackupData> allBackups = getAllBackupsFromCurrentDb();
        
        if (allBackups.isEmpty()) {
            return "§cno backups found to export";
        }
        
        try (FileWriter writer = new FileWriter(exportFile)) {
            writer.write("-- InvRewind MySQL Export\n");
            writer.write("-- Generated: " + new java.util.Date() + "\n");
            writer.write("-- Total backups: " + allBackups.size() + "\n\n");
            
            writer.write("DROP TABLE IF EXISTS invrewind_backups;\n\n");
            
            writer.write("""
                CREATE TABLE invrewind_backups (
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
                );
                
                """);
            
            int count = 0;
            for (BackupData backup : allBackups) {
                writer.write("INSERT INTO invrewind_backups VALUES (");
                writer.write(backup.getId() + ", ");
                writer.write("'" + backup.getPlayerUuid().toString() + "', ");
                writer.write("'" + escapeSql(backup.getPlayerName()) + "', ");
                writer.write("'" + backup.getBackupType().getKey() + "', ");
                writer.write(backup.getTimestamp() + ", ");
                
                if (backup.getLocation() != null) {
                    Location loc = backup.getLocation();
                    writer.write("'" + escapeSql(loc.getWorld().getName()) + "', ");
                    writer.write(loc.getX() + ", ");
                    writer.write(loc.getY() + ", ");
                    writer.write(loc.getZ() + ", ");
                    writer.write(loc.getYaw() + ", ");
                    writer.write(loc.getPitch() + ", ");
                } else {
                    writer.write("NULL, NULL, NULL, NULL, NULL, NULL, ");
                }
                
                writer.write(backup.getHealth() + ", ");
                writer.write(backup.getHunger() + ", ");
                writer.write(backup.getXpLevel() + ", ");
                writer.write(backup.getXpProgress() + ", ");
                writer.write(backup.getInventory() != null ? "'" + escapeSql(ItemSerializer.serializeItems(backup.getInventory())) + "'" : "NULL");
                writer.write(", ");
                writer.write(backup.getArmor() != null ? "'" + escapeSql(ItemSerializer.serializeItems(backup.getArmor())) + "'" : "NULL");
                writer.write(", ");
                writer.write(backup.getOffhand() != null ? "'" + escapeSql(ItemSerializer.serializeItem(backup.getOffhand())) + "'" : "NULL");
                writer.write(", ");
                writer.write(backup.getEnderchest() != null ? "'" + escapeSql(ItemSerializer.serializeItems(backup.getEnderchest())) + "'" : "NULL");
                writer.write(");\n");
                
                count++;
                if (count % 100 == 0) {
                    plugin.getLogger().info("Exported " + count + "/" + allBackups.size() + " backups...");
                }
            }
            
            return "§aexported " + count + " backups to: exports/mysql/export_" + timestamp + ".sql";
        }
    }

    private List<BackupData> getAllBackupsFromCurrentDb() throws Exception {
        List<BackupData> backups = new ArrayList<>();
        
        if (databaseManager.isYamlMode()) {
            YamlDatabaseManager yamlDb = new YamlDatabaseManager(plugin);
            File backupsFolder = new File(plugin.getDataFolder(), 
                configManager.getConfig().getString("database.yaml.folder", "backups"));
            
            for (BackupData.BackupType type : BackupData.BackupType.values()) {
                File typeFolder = new File(backupsFolder, type.getKey());
                if (!typeFolder.exists()) continue;
                
                File[] playerFolders = typeFolder.listFiles(File::isDirectory);
                if (playerFolders == null) continue;
                
                for (File playerFolder : playerFolders) {
                    File[] backupFiles = playerFolder.listFiles((dir, name) -> 
                        name.startsWith("backup_") && name.endsWith(".yml"));
                    
                    if (backupFiles == null) continue;
                    
                    for (File backupFile : backupFiles) {
                        BackupData backup = loadYamlBackup(backupFile);
                        if (backup != null) {
                            backups.add(backup);
                        }
                    }
                }
            }
        } else {
            String sql = "SELECT * FROM invrewind_backups ORDER BY id";
            
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                
                while (rs.next()) {
                    backups.add(parseBackupData(rs));
                }
            }
        }
        
        return backups;
    }

    private BackupData loadYamlBackup(File backupFile) {
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(backupFile);
            
            int id = yaml.getInt("id");
            UUID playerUuid = UUID.fromString(yaml.getString("player-uuid"));
            String playerName = yaml.getString("player-name");
            BackupData.BackupType type = BackupData.BackupType.fromKey(yaml.getString("backup-type"));
            long timestamp = yaml.getLong("timestamp");
            
            Location location = null;
            if (yaml.contains("location.world")) {
                String worldName = yaml.getString("location.world");
                org.bukkit.World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    location = new Location(
                        world,
                        yaml.getDouble("location.x"),
                        yaml.getDouble("location.y"),
                        yaml.getDouble("location.z"),
                        (float) yaml.getDouble("location.yaw"),
                        (float) yaml.getDouble("location.pitch")
                    );
                }
            }
            
            double health = yaml.getDouble("health", 20.0);
            int hunger = yaml.getInt("hunger", 20);
            int xpLevel = yaml.getInt("xp-level", 0);
            float xpProgress = (float) yaml.getDouble("xp-progress", 0.0);
            
            org.bukkit.inventory.ItemStack[] inventory = null;
            org.bukkit.inventory.ItemStack[] armor = null;
            org.bukkit.inventory.ItemStack offhand = null;
            org.bukkit.inventory.ItemStack[] enderchest = null;
            
            if (yaml.contains("inventory")) {
                List<?> invList = yaml.getList("inventory");
                if (invList != null) {
                    inventory = invList.toArray(new org.bukkit.inventory.ItemStack[0]);
                }
            }
            
            if (yaml.contains("armor")) {
                List<?> armorList = yaml.getList("armor");
                if (armorList != null) {
                    armor = armorList.toArray(new org.bukkit.inventory.ItemStack[0]);
                }
            }
            
            if (yaml.contains("offhand")) {
                offhand = yaml.getItemStack("offhand");
            }
            
            if (yaml.contains("enderchest")) {
                List<?> ecList = yaml.getList("enderchest");
                if (ecList != null) {
                    enderchest = ecList.toArray(new org.bukkit.inventory.ItemStack[0]);
                }
            }
            
            return new BackupData(id, playerUuid, playerName, type, timestamp, location,
                                 health, hunger, xpLevel, xpProgress, inventory, armor, offhand, enderchest);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load backup from " + backupFile.getName(), e);
            return null;
        }
    }

    private BackupData parseBackupData(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
        String playerName = rs.getString("player_name");
        BackupData.BackupType type = BackupData.BackupType.fromKey(rs.getString("backup_type"));
        long timestamp = rs.getLong("timestamp");
        
        String worldName = rs.getString("world");
        Location location = null;
        if (worldName != null && !worldName.isEmpty()) {
            org.bukkit.World world = Bukkit.getWorld(worldName);
            if (world != null) {
                location = new Location(
                    world,
                    rs.getDouble("x"),
                    rs.getDouble("y"),
                    rs.getDouble("z"),
                    rs.getFloat("yaw"),
                    rs.getFloat("pitch")
                );
            }
        }
        
        double health = rs.getDouble("health");
        int hunger = rs.getInt("hunger");
        int xpLevel = rs.getInt("xp_level");
        float xpProgress = rs.getFloat("xp_progress");
        
        org.bukkit.inventory.ItemStack[] inventory = null;
        org.bukkit.inventory.ItemStack[] armor = null;
        org.bukkit.inventory.ItemStack offhand = null;
        org.bukkit.inventory.ItemStack[] enderchest = null;
        
        try {
            String inventoryData = rs.getString("inventory_data");
            if (inventoryData != null && !inventoryData.isEmpty()) {
                inventory = ItemSerializer.deserializeItems(inventoryData);
            }
        } catch (Exception ignored) {}
        
        try {
            String armorData = rs.getString("armor_data");
            if (armorData != null && !armorData.isEmpty()) {
                armor = ItemSerializer.deserializeItems(armorData);
            }
        } catch (Exception ignored) {}
        
        try {
            String offhandData = rs.getString("offhand_data");
            if (offhandData != null && !offhandData.isEmpty()) {
                offhand = ItemSerializer.deserializeItem(offhandData);
            }
        } catch (Exception ignored) {}
        
        try {
            String enderchestData = rs.getString("enderchest_data");
            if (enderchestData != null && !enderchestData.isEmpty()) {
                enderchest = ItemSerializer.deserializeItems(enderchestData);
            }
        } catch (Exception ignored) {}
        
        return new BackupData(id, playerUuid, playerName, type, timestamp, location,
                             health, hunger, xpLevel, xpProgress, inventory, armor, offhand, enderchest);
    }

    private void createSqliteTables(Connection conn) throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS invrewind_backups (
                id INTEGER PRIMARY KEY,
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
        
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    private String escapeSql(String str) {
        if (str == null) return "";
        return str.replace("'", "''").replace("\\", "\\\\");
    }

    @NotNull
    public List<String> getAvailableImports(String type) {
        List<String> files = new ArrayList<>();
        File folder = new File(importsFolder, type.toLowerCase());
        
        if (!folder.exists()) {
            return files;
        }
        
        if (type.equalsIgnoreCase("yaml")) {
            File[] dirs = folder.listFiles(File::isDirectory);
            if (dirs != null) {
                for (File dir : dirs) {
                    files.add(dir.getName());
                }
            }
        } else {
            String extension = type.equalsIgnoreCase("sqlite") ? ".db" : ".sql";
            File[] fileList = folder.listFiles((dir, name) -> name.endsWith(extension));
            if (fileList != null) {
                for (File file : fileList) {
                    files.add(file.getName());
                }
            }
        }
        
        return files;
    }

    @NotNull
    public CompletableFuture<String> importData(@NotNull String sourceType, @NotNull String filename) {
        CompletableFuture<String> future = new CompletableFuture<>();
        
        SchedulerUtil.runTaskAsynchronously(plugin, () -> {
            try {
                String result;
                
                switch (sourceType.toLowerCase()) {
                    case "yaml" -> result = importFromYaml(filename);
                    case "sqlite" -> result = importFromSqlite(filename);
                    case "mysql" -> result = importFromMysql(filename);
                    default -> {
                        future.complete("§cInvalid import type: " + sourceType);
                        return;
                    }
                }
                
                future.complete(result);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Import failed", e);
                future.complete("§cImport failed: " + e.getMessage());
            }
        });
        
        return future;
    }

    private String importFromYaml(String foldername) throws Exception {
        File importFolder = new File(importsFolder, "yaml/" + foldername);
        
        if (!importFolder.exists() || !importFolder.isDirectory()) {
            return "§cimport folder not found: " + foldername;
        }
        
        List<BackupData> backups = new ArrayList<>();
        
        for (BackupData.BackupType type : BackupData.BackupType.values()) {
            File typeFolder = new File(importFolder, type.getKey());
            if (!typeFolder.exists()) continue;
            
            File[] playerFolders = typeFolder.listFiles(File::isDirectory);
            if (playerFolders == null) continue;
            
            for (File playerFolder : playerFolders) {
                File[] backupFiles = playerFolder.listFiles((dir, name) -> 
                    name.startsWith("backup_") && name.endsWith(".yml"));
                
                if (backupFiles == null) continue;
                
                for (File backupFile : backupFiles) {
                    BackupData backup = loadYamlBackup(backupFile);
                    if (backup != null) {
                        backups.add(backup);
                    }
                }
            }
        }
        
        if (backups.isEmpty()) {
            return "§cno backups found in import folder";
        }
        
        int imported = importBackupsToCurrentDb(backups);
        
        if (configManager.getConfig().getBoolean("migration.delete-source-after-import", false)) {
            deleteDirectory(importFolder);
        }
        
        return "§aimported " + imported + " backups from: " + foldername;
    }

    private String importFromSqlite(String filename) throws Exception {
        File importFile = new File(importsFolder, "sqlite/" + filename);
        
        if (!importFile.exists()) {
            return "§cimport file not found: " + filename;
        }
        
        String url = "jdbc:sqlite:" + importFile.getAbsolutePath();
        List<BackupData> backups = new ArrayList<>();
        
        try (Connection conn = DriverManager.getConnection(url)) {
            String sql = "SELECT * FROM invrewind_backups";
            
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                
                while (rs.next()) {
                    backups.add(parseBackupData(rs));
                }
            }
        }
        
        if (backups.isEmpty()) {
            return "§cno backups found in import file";
        }
        
        int imported = importBackupsToCurrentDb(backups);
        
        if (configManager.getConfig().getBoolean("migration.delete-source-after-import", false)) {
            importFile.delete();
        }
        
        return "§aimported " + imported + " backups from: " + filename;
    }

    private String importFromMysql(String filename) throws Exception {
        File importFile = new File(importsFolder, "mysql/" + filename);
        
        if (!importFile.exists()) {
            return "§cimport file not found: " + filename;
        }
        
        String currentDbType = databaseManager.getActualDatabaseType();
        
        if (currentDbType.equalsIgnoreCase("yaml")) {
            return "§ccannot import SQL file to YAML storage. please configure MySQL or SQLite database first.";
        }
        
        if (!currentDbType.equalsIgnoreCase("mysql") && !currentDbType.equalsIgnoreCase("sqlite")) {
            return "§ccurrent database type (" + currentDbType + ") does not support SQL import. please use MySQL or SQLite.";
        }
        
        try (Connection conn = databaseManager.getConnection()) {
            if (conn == null || conn.isClosed()) {
                return "§cdatabase connection failed. please check your database configuration and ensure the database is accessible.";
            }
        } catch (SQLException e) {
            return "§cdatabase connection failed: " + e.getMessage() + ". please verify your database settings.";
        }
        
        String content = Files.readString(importFile.toPath());
        
        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            
            String[] statements = content.split(";");
            int count = 0;
            
            for (String sql : statements) {
                sql = sql.trim();
                if (!sql.isEmpty() && !sql.startsWith("--")) {
                    stmt.execute(sql);
                    if (sql.toUpperCase().startsWith("INSERT")) {
                        count++;
                        if (count % 100 == 0) {
                            plugin.getLogger().info("Imported " + count + " backups...");
                        }
                    }
                }
            }
            
            if (configManager.getConfig().getBoolean("migration.delete-source-after-import", false)) {
                importFile.delete();
            }
            
            return "§aimported SQL file: " + filename + " (" + count + " backups)";
        }
    }

    private int importBackupsToCurrentDb(List<BackupData> backups) throws Exception {
        int count = 0;
        int skipped = 0;
        
        if (databaseManager.isYamlMode()) {
            YamlDatabaseManager yamlDb = new YamlDatabaseManager(plugin);
            
            for (BackupData backup : backups) {
                if (yamlDb.saveBackup(backup)) {
                    count++;
                    if (count % 100 == 0) {
                        plugin.getLogger().info("Imported " + count + "/" + backups.size() + " backups...");
                    }
                }
            }
        } else {
            String checkSql = "SELECT COUNT(*) FROM invrewind_backups WHERE id = ?";
            String insertSql = """
                INSERT INTO invrewind_backups
                (id, player_uuid, player_name, backup_type, timestamp, world, x, y, z, yaw, pitch,
                 health, hunger, xp_level, xp_progress, inventory_data, armor_data, offhand_data, enderchest_data)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
            
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement checkStmt = conn.prepareStatement(checkSql);
                 PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                
                for (BackupData backup : backups) {
                    checkStmt.setInt(1, backup.getId());
                    try (ResultSet rs = checkStmt.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            skipped++;
                            continue;
                        }
                    }
                    
                    insertStmt.setInt(1, backup.getId());
                    insertStmt.setString(2, backup.getPlayerUuid().toString());
                    insertStmt.setString(3, backup.getPlayerName());
                    insertStmt.setString(4, backup.getBackupType().getKey());
                    insertStmt.setLong(5, backup.getTimestamp());
                    
                    if (backup.getLocation() != null) {
                        Location loc = backup.getLocation();
                        insertStmt.setString(6, loc.getWorld().getName());
                        insertStmt.setDouble(7, loc.getX());
                        insertStmt.setDouble(8, loc.getY());
                        insertStmt.setDouble(9, loc.getZ());
                        insertStmt.setFloat(10, loc.getYaw());
                        insertStmt.setFloat(11, loc.getPitch());
                    } else {
                        insertStmt.setNull(6, Types.VARCHAR);
                        insertStmt.setNull(7, Types.DOUBLE);
                        insertStmt.setNull(8, Types.DOUBLE);
                        insertStmt.setNull(9, Types.DOUBLE);
                        insertStmt.setNull(10, Types.FLOAT);
                        insertStmt.setNull(11, Types.FLOAT);
                    }
                    
                    insertStmt.setDouble(12, backup.getHealth());
                    insertStmt.setInt(13, backup.getHunger());
                    insertStmt.setInt(14, backup.getXpLevel());
                    insertStmt.setFloat(15, backup.getXpProgress());
                    insertStmt.setString(16, backup.getInventory() != null ? ItemSerializer.serializeItems(backup.getInventory()) : null);
                    insertStmt.setString(17, backup.getArmor() != null ? ItemSerializer.serializeItems(backup.getArmor()) : null);
                    insertStmt.setString(18, backup.getOffhand() != null ? ItemSerializer.serializeItem(backup.getOffhand()) : null);
                    insertStmt.setString(19, backup.getEnderchest() != null ? ItemSerializer.serializeItems(backup.getEnderchest()) : null);
                    
                    insertStmt.executeUpdate();
                    count++;
                    
                    if (count % 100 == 0) {
                        plugin.getLogger().info("Imported " + count + "/" + backups.size() + " backups...");
                    }
                }
            }
        }
        
        if (skipped > 0) {
            plugin.getLogger().info("Skipped " + skipped + " duplicate backups");
        }
        
        return count;
    }

    private void deleteDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }
}
