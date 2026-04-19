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
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class BackupManager {

    private final InvRewind plugin;
    private final DatabaseManager databaseManager;
    private final YamlDatabaseManager yamlDatabaseManager;
    private final ConfigManager configManager;
    private final Map<UUID, List<BackupData>> cache;

    public BackupManager(@NotNull InvRewind plugin, @NotNull DatabaseManager databaseManager,
                         @NotNull ConfigManager configManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.yamlDatabaseManager = new YamlDatabaseManager(plugin);
        this.configManager = configManager;
        this.cache = new ConcurrentHashMap<>();
    }

    private boolean isYamlMode() {
        return databaseManager.isYamlMode();
    }

    @NotNull
    public CompletableFuture<Boolean> createBackup(@NotNull Player player, @NotNull BackupData.BackupType type) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        ItemStack[] inventory = null;
        ItemStack[] armor = null;
        ItemStack offhand = null;
        ItemStack[] enderchest = null;
        Location loc = null;
        double health = 0;
        int hunger = 0;
        int xpLevel = 0;
        float xpProgress = 0;

        if (configManager.getConfig().getBoolean("features.save-inventory", true)) {
            ItemStack[] original = player.getInventory().getContents();
            inventory = new ItemStack[original.length];
            for (int i = 0; i < original.length; i++) {
                inventory[i] = original[i] != null ? original[i].clone() : null;
            }

            if (configManager.getConfig().getBoolean("debug.enabled", false)) {
                int itemCount = 0;
                for (ItemStack item : inventory) {
                    if (item != null && item.getType() != org.bukkit.Material.AIR) {
                        itemCount++;
                    }
                }
                plugin.getLogger().info("Captured " + itemCount + " items from inventory for " + player.getName());
            }
        }

        if (configManager.getConfig().getBoolean("features.save-armor", true)) {
            ItemStack[] original = player.getInventory().getArmorContents();
            armor = new ItemStack[original.length];
            for (int i = 0; i < original.length; i++) {
                armor[i] = original[i] != null ? original[i].clone() : null;
            }

            if (configManager.getConfig().getBoolean("debug.enabled", false)) {
                int itemCount = 0;
                for (ItemStack item : armor) {
                    if (item != null && item.getType() != org.bukkit.Material.AIR) {
                        itemCount++;
                    }
                }
                plugin.getLogger().info("Captured " + itemCount + " armor pieces for " + player.getName());
            }
        }

        if (configManager.getConfig().getBoolean("features.save-offhand", true)) {
            ItemStack original = player.getInventory().getItemInOffHand();
            offhand = (original != null && original.getType() != org.bukkit.Material.AIR) ? original.clone() : null;

            if (configManager.getConfig().getBoolean("debug.enabled", false) && offhand != null) {
                plugin.getLogger().info("Captured offhand item: " + offhand.getType() + " for " + player.getName());
            }
        }

        if (configManager.getConfig().getBoolean("features.save-enderchest", true)) {
            ItemStack[] original = player.getEnderChest().getContents();
            enderchest = new ItemStack[original.length];
            for (int i = 0; i < original.length; i++) {
                enderchest[i] = original[i] != null ? original[i].clone() : null;
            }

            if (configManager.getConfig().getBoolean("debug.enabled", false)) {
                int itemCount = 0;
                for (ItemStack item : enderchest) {
                    if (item != null && item.getType() != org.bukkit.Material.AIR) {
                        itemCount++;
                    }
                }
                plugin.getLogger().info("Captured " + itemCount + " items from ender chest for " + player.getName());
            }
        }

        if (configManager.getConfig().getBoolean("features.save-location", true)) {
            loc = player.getLocation().clone();
        }

        if (configManager.getConfig().getBoolean("features.save-health", true)) {
            health = player.getHealth();
        }

        if (configManager.getConfig().getBoolean("features.save-hunger", true)) {
            hunger = player.getFoodLevel();
        }

        if (configManager.getConfig().getBoolean("features.save-xp", true)) {
            xpLevel = player.getLevel();
            xpProgress = player.getExp();
        }

        final ItemStack[] finalInventory = inventory;
        final ItemStack[] finalArmor = armor;
        final ItemStack finalOffhand = offhand;
        final ItemStack[] finalEnderchest = enderchest;
        final Location finalLoc = loc;
        final double finalHealth = health;
        final int finalHunger = hunger;
        final int finalXpLevel = xpLevel;
        final float finalXpProgress = xpProgress;

        SchedulerUtil.runTaskAsynchronously(plugin, () -> {
            try {

                if (isYamlMode()) {

                    int id = yamlDatabaseManager.getNextId(player.getUniqueId(), type);
                    BackupData backup = new BackupData(
                        id, player.getUniqueId(), player.getName(), type, System.currentTimeMillis(),
                        finalLoc, finalHealth, finalHunger, finalXpLevel, finalXpProgress,
                        finalInventory, finalArmor, finalOffhand, finalEnderchest
                    );

                    boolean saved = yamlDatabaseManager.saveBackup(backup);
                    if (saved) {
                        cache.remove(player.getUniqueId());
                        cleanupOldBackups(player.getUniqueId(), type);
                        future.complete(true);
                    } else {
                        future.complete(false);
                    }
                } else {

                    createBackupSQL(player, type, finalLoc, finalHealth, finalHunger, finalXpLevel, finalXpProgress,
                                   finalInventory, finalArmor, finalOffhand, finalEnderchest, future);
                }

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Unexpected error creating backup", e);
                future.complete(false);
            }
        });

        return future;
    }

    private void createBackupSQL(@NotNull Player player, @NotNull BackupData.BackupType type,
                                 @Nullable Location loc, double health, int hunger, int xpLevel, float xpProgress,
                                 @Nullable ItemStack[] inventory, @Nullable ItemStack[] armor,
                                 @Nullable ItemStack offhand, @Nullable ItemStack[] enderchest,
                                 @NotNull CompletableFuture<Boolean> future) {
        try {

            String inventoryData = inventory != null ? ItemSerializer.serializeItems(inventory) : null;
            String armorData = armor != null ? ItemSerializer.serializeItems(armor) : null;
            String offhandData = offhand != null ? ItemSerializer.serializeItem(offhand) : null;
            String enderchestData = enderchest != null ? ItemSerializer.serializeItems(enderchest) : null;

            String sql = """
                INSERT INTO invrewind_backups
                (player_uuid, player_name, backup_type, timestamp, world, x, y, z, yaw, pitch,
                 health, hunger, xp_level, xp_progress, inventory_data, armor_data, offhand_data, enderchest_data)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, player.getUniqueId().toString());
                stmt.setString(2, player.getName());
                stmt.setString(3, type.getKey());
                stmt.setLong(4, System.currentTimeMillis());

                if (loc != null) {
                    stmt.setString(5, loc.getWorld().getName());
                    stmt.setDouble(6, loc.getX());
                    stmt.setDouble(7, loc.getY());
                    stmt.setDouble(8, loc.getZ());
                    stmt.setFloat(9, loc.getYaw());
                    stmt.setFloat(10, loc.getPitch());
                } else {
                    stmt.setNull(5, java.sql.Types.VARCHAR);
                    stmt.setNull(6, java.sql.Types.DOUBLE);
                    stmt.setNull(7, java.sql.Types.DOUBLE);
                    stmt.setNull(8, java.sql.Types.DOUBLE);
                    stmt.setNull(9, java.sql.Types.FLOAT);
                    stmt.setNull(10, java.sql.Types.FLOAT);
                }

                stmt.setDouble(11, health);
                stmt.setInt(12, hunger);
                stmt.setInt(13, xpLevel);
                stmt.setFloat(14, xpProgress);
                stmt.setString(15, inventoryData);
                stmt.setString(16, armorData);
                stmt.setString(17, offhandData);
                stmt.setString(18, enderchestData);

                stmt.executeUpdate();
            }

            if (!verifyBackupCreated(player.getUniqueId(), type)) {
                plugin.getLogger().warning("Backup verification failed for " + player.getName());
                future.complete(false);
                return;
            }

            cache.remove(player.getUniqueId());

            cleanupOldBackups(player.getUniqueId(), type);

            future.complete(true);

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create backup for " + player.getName(), e);
            future.complete(false);
        }
    }

    private void cleanupOldBackups(@NotNull UUID playerUuid, @NotNull BackupData.BackupType type) {

        SchedulerUtil.runTaskAsynchronously(plugin, () -> {
            try {

                if (!configManager.getConfig().getBoolean("auto-backup.limits.enabled", true)) {
                    return;
                }

                String typeKey = type.getKey().replace("_", "-");
                int maxBackups = configManager.getConfig().getInt("auto-backup.limits.max-per-type." + typeKey, 50);

                if (maxBackups == -1) {
                    return;
                }

                if (isYamlMode()) {

                    String playerName = Bukkit.getOfflinePlayer(playerUuid).getName();
                    if (playerName == null) playerName = playerUuid.toString();
                    yamlDatabaseManager.cleanupOldBackups(playerUuid, playerName, type, maxBackups);
                } else {

                    cleanupOldBackupsSQL(playerUuid, type, maxBackups);
                }

            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to cleanup old backups", e);
            }
        });
    }

    private void cleanupOldBackupsSQL(@NotNull UUID playerUuid, @NotNull BackupData.BackupType type, int maxBackups) {
        try {

            String countSql = "SELECT COUNT(*) FROM invrewind_backups WHERE player_uuid = ? AND backup_type = ?";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement countStmt = conn.prepareStatement(countSql)) {

                countStmt.setString(1, playerUuid.toString());
                countStmt.setString(2, type.getKey());

                try (ResultSet rs = countStmt.executeQuery()) {
                    if (rs.next()) {
                        int count = rs.getInt(1);
                        if (count <= maxBackups) {
                            return;
                        }
                    }
                }

                String deleteSql = """
                    DELETE FROM invrewind_backups
                    WHERE player_uuid = ? AND backup_type = ? AND id NOT IN (
                        SELECT id FROM invrewind_backups
                        WHERE player_uuid = ? AND backup_type = ?
                        ORDER BY timestamp DESC
                        LIMIT ?
                    )
                    """;

                try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                    deleteStmt.setString(1, playerUuid.toString());
                    deleteStmt.setString(2, type.getKey());
                    deleteStmt.setString(3, playerUuid.toString());
                    deleteStmt.setString(4, type.getKey());
                    deleteStmt.setInt(5, maxBackups);
                    int deleted = deleteStmt.executeUpdate();

                    if (deleted > 0 && configManager.getConfig().getBoolean("debug.enabled", false)) {
                        plugin.getLogger().info("Cleaned up " + deleted + " old " + type.getKey() + " backups for " + playerUuid);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to cleanup old SQL backups", e);
        }
    }

    @NotNull
    public CompletableFuture<List<BackupData>> getBackups(@NotNull UUID playerUuid) {
        CompletableFuture<List<BackupData>> future = new CompletableFuture<>();

        SchedulerUtil.runTaskAsynchronously(plugin, () -> {
            try {

                if (cache.containsKey(playerUuid)) {
                    future.complete(cache.get(playerUuid));
                    return;
                }

                List<BackupData> backups;

                if (isYamlMode()) {

                    String playerName = Bukkit.getOfflinePlayer(playerUuid).getName();
                    if (playerName == null) playerName = playerUuid.toString();
                    backups = yamlDatabaseManager.getBackups(playerUuid, playerName);
                } else {

                    backups = getBackupsSQL(playerUuid);
                }

                cache.put(playerUuid, backups);
                future.complete(backups);

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Unexpected error getting backups", e);
                future.complete(new ArrayList<>());
            }
        });

        return future;
    }

    @NotNull
    private List<BackupData> getBackupsSQL(@NotNull UUID playerUuid) {
        List<BackupData> backups = new ArrayList<>();

        String sql = """
            SELECT * FROM invrewind_backups
            WHERE player_uuid = ?
            ORDER BY timestamp DESC
            """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, playerUuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    backups.add(parseBackupData(rs));
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get backups for " + playerUuid, e);
        }

        return backups;
    }

    @NotNull
    public CompletableFuture<List<BackupData>> getBackupsByType(@NotNull UUID playerUuid, @NotNull BackupData.BackupType type) {
        CompletableFuture<List<BackupData>> future = new CompletableFuture<>();

        SchedulerUtil.runTaskAsynchronously(plugin, () -> {
            try {
                List<BackupData> backups;

                if (isYamlMode()) {

                    String playerName = Bukkit.getOfflinePlayer(playerUuid).getName();
                    if (playerName == null) playerName = playerUuid.toString();
                    backups = yamlDatabaseManager.getBackupsByType(playerUuid, playerName, type);
                } else {

                    backups = getBackupsByTypeSQL(playerUuid, type);
                }

                future.complete(backups);

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Unexpected error getting backups by type", e);
                future.complete(new ArrayList<>());
            }
        });

        return future;
    }

    @NotNull
    private List<BackupData> getBackupsByTypeSQL(@NotNull UUID playerUuid, @NotNull BackupData.BackupType type) {
        List<BackupData> backups = new ArrayList<>();

        String sql = """
            SELECT * FROM invrewind_backups
            WHERE player_uuid = ? AND backup_type = ?
            ORDER BY timestamp DESC
            """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, type.getKey());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    backups.add(parseBackupData(rs));
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get backups by type", e);
        }

        return backups;
    }

    @Nullable
    public CompletableFuture<BackupData> getBackup(int backupId) {
        CompletableFuture<BackupData> future = new CompletableFuture<>();

        SchedulerUtil.runTaskAsynchronously(plugin, () -> {
            try {
                BackupData backup = null;

                if (isYamlMode()) {

                    plugin.getLogger().warning("getBackup(id) is inefficient with YAML storage. Consider using getBackup with player info.");
                    future.complete(null);
                } else {

                    backup = getBackupSQL(backupId);
                }

                future.complete(backup);

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Unexpected error getting backup", e);
                future.complete(null);
            }
        });

        return future;
    }

    @Nullable
    private BackupData getBackupSQL(int backupId) {
        String sql = "SELECT * FROM invrewind_backups WHERE id = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, backupId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return parseBackupData(rs);
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get backup " + backupId, e);
        }

        return null;
    }

    @NotNull
    private BackupData parseBackupData(@NotNull ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
        String playerName = rs.getString("player_name");
        BackupData.BackupType type = BackupData.BackupType.fromKey(rs.getString("backup_type"));
        long timestamp = rs.getLong("timestamp");

        String worldName = rs.getString("world");
        Location location = null;
        if (worldName != null && !worldName.isEmpty()) {
            try {
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
                } else {
                    plugin.getLogger().warning("World '" + worldName + "' not found for backup #" + id);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to parse location for backup #" + id + ": " + e.getMessage());
            }
        }

        double health = rs.getDouble("health");
        if (rs.wasNull()) health = 20.0;

        int hunger = rs.getInt("hunger");
        if (rs.wasNull()) hunger = 20;

        int xpLevel = rs.getInt("xp_level");
        if (rs.wasNull()) xpLevel = 0;

        float xpProgress = rs.getFloat("xp_progress");
        if (rs.wasNull()) xpProgress = 0.0f;

        ItemStack[] inventory = null;
        ItemStack[] armor = null;
        ItemStack offhand = null;
        ItemStack[] enderchest = null;

        try {
            String inventoryData = rs.getString("inventory_data");
            if (inventoryData != null && !inventoryData.isEmpty()) {
                inventory = ItemSerializer.deserializeItems(inventoryData);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to deserialize inventory for backup #" + id + ": " + e.getMessage());
        }

        try {
            String armorData = rs.getString("armor_data");
            if (armorData != null && !armorData.isEmpty()) {
                armor = ItemSerializer.deserializeItems(armorData);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to deserialize armor for backup #" + id + ": " + e.getMessage());
        }

        try {
            String offhandData = rs.getString("offhand_data");
            if (offhandData != null && !offhandData.isEmpty()) {
                offhand = ItemSerializer.deserializeItem(offhandData);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to deserialize offhand for backup #" + id + ": " + e.getMessage());
        }

        try {
            String enderchestData = rs.getString("enderchest_data");
            if (enderchestData != null && !enderchestData.isEmpty()) {
                enderchest = ItemSerializer.deserializeItems(enderchestData);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to deserialize enderchest for backup #" + id + ": " + e.getMessage());
        }

        return new BackupData(id, playerUuid, playerName, type, timestamp, location,
                             health, hunger, xpLevel, xpProgress, inventory, armor, offhand, enderchest);
    }

    public void clearCache() {
        cache.clear();
    }

    public void clearCache(@NotNull UUID playerUuid) {
        cache.remove(playerUuid);
    }

    @NotNull
    public CompletableFuture<List<Map.Entry<UUID, String>>> getAllPlayersWithBackups() {
        CompletableFuture<List<Map.Entry<UUID, String>>> future = new CompletableFuture<>();

        SchedulerUtil.runTaskAsynchronously(plugin, () -> {
            try {
                Map<UUID, String> playersMap = new java.util.LinkedHashMap<>();

                if (isYamlMode()) {
                    java.io.File backupsFolder = new java.io.File(plugin.getDataFolder(), "backups");
                    plugin.getLogger().info("[DEBUG] Scanning YAML backups folder: " + backupsFolder.getAbsolutePath());
                    
                    if (backupsFolder.exists() && backupsFolder.isDirectory()) {
                        java.io.File[] typeFolders = backupsFolder.listFiles();
                        plugin.getLogger().info("[DEBUG] Found " + (typeFolders != null ? typeFolders.length : 0) + " type folders");
                        
                        if (typeFolders != null) {
                            for (java.io.File typeFolder : typeFolders) {
                                if (typeFolder.isDirectory()) {
                                    plugin.getLogger().info("[DEBUG] Scanning type folder: " + typeFolder.getName());
                                    java.io.File[] playerFolders = typeFolder.listFiles();
                                    plugin.getLogger().info("[DEBUG] Found " + (playerFolders != null ? playerFolders.length : 0) + " player folders in " + typeFolder.getName());
                                    
                                    if (playerFolders != null) {
                                        for (java.io.File playerFolder : playerFolders) {
                                            if (playerFolder.isDirectory()) {
                                                String playerName = playerFolder.getName();
                                                plugin.getLogger().info("[DEBUG] Checking player folder: " + playerName);
                                                
                                                java.io.File[] backupFiles = playerFolder.listFiles((dir, name) -> name.endsWith(".yml"));
                                                plugin.getLogger().info("[DEBUG] Found " + (backupFiles != null ? backupFiles.length : 0) + " backup files for " + playerName);
                                                
                                                if (backupFiles != null && backupFiles.length > 0) {
                                                    try {
                                                        plugin.getLogger().info("[DEBUG] Reading backup file: " + backupFiles[0].getName());
                                                        org.bukkit.configuration.file.YamlConfiguration yaml = 
                                                            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(backupFiles[0]);
                                                        String uuidStr = yaml.getString("player-uuid");
                                                        plugin.getLogger().info("[DEBUG] UUID from file: " + uuidStr);
                                                        if (uuidStr != null) {
                                                            UUID uuid = UUID.fromString(uuidStr);
                                                            playersMap.putIfAbsent(uuid, playerName);
                                                            plugin.getLogger().info("[DEBUG] Added player: " + playerName + " (" + uuid + ")");
                                                        } else {
                                                            plugin.getLogger().warning("[DEBUG] No UUID found in backup file for " + playerName);
                                                        }
                                                    } catch (Exception e) {
                                                        plugin.getLogger().warning("Failed to read UUID from backup file for " + playerName + ": " + e.getMessage());
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        plugin.getLogger().warning("[DEBUG] Backups folder does not exist or is not a directory");
                    }
                    plugin.getLogger().info("[DEBUG] Total players found: " + playersMap.size());
                } else {
                    String sql = "SELECT DISTINCT player_uuid, player_name FROM invrewind_backups ORDER BY player_name";
                    try (Connection conn = databaseManager.getConnection();
                         PreparedStatement stmt = conn.prepareStatement(sql);
                         ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                            String name = rs.getString("player_name");
                            playersMap.put(uuid, name);
                        }
                    } catch (SQLException e) {
                        plugin.getLogger().log(Level.SEVERE, "Failed to get all players with backups", e);
                    }
                }

                List<Map.Entry<UUID, String>> sortedPlayers = new ArrayList<>(playersMap.entrySet());
                sortedPlayers.sort((a, b) -> {
                    Player playerA = Bukkit.getPlayer(a.getKey());
                    Player playerB = Bukkit.getPlayer(b.getKey());
                    boolean onlineA = playerA != null && playerA.isOnline();
                    boolean onlineB = playerB != null && playerB.isOnline();
                    
                    if (onlineA && !onlineB) return -1;
                    if (!onlineA && onlineB) return 1;
                    return a.getValue().compareToIgnoreCase(b.getValue());
                });

                future.complete(sortedPlayers);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Unexpected error getting all players", e);
                future.complete(new ArrayList<>());
            }
        });

        return future;
    }

    private boolean verifyBackupCreated(@NotNull UUID playerUuid, @NotNull BackupData.BackupType type) {
        try {
            String sql = "SELECT COUNT(*) FROM invrewind_backups WHERE player_uuid = ? AND backup_type = ? AND timestamp > ?";
            long recentTime = System.currentTimeMillis() - 5000;

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, type.getKey());
                stmt.setLong(3, recentTime);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1) > 0;
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to verify backup creation", e);
        }
        return false;
    }
}
