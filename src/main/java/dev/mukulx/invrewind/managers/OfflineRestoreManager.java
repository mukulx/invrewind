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
import dev.mukulx.invrewind.model.BackupData;
import dev.mukulx.invrewind.model.PendingRestore;
import dev.mukulx.invrewind.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class OfflineRestoreManager {

    private final InvRewind plugin;
    private final DatabaseManager databaseManager;
    private final ConfigManager configManager;
    private final Map<UUID, PendingRestore> pendingRestores;
    private final Set<UUID> processingPlayers;
    private BukkitTask expiryCheckTask;

    public OfflineRestoreManager(@NotNull InvRewind plugin, @NotNull DatabaseManager databaseManager,
                                 @NotNull ConfigManager configManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.configManager = configManager;
        this.pendingRestores = new ConcurrentHashMap<>();
        this.processingPlayers = ConcurrentHashMap.newKeySet();
    }

    public void initialize() {
        if (databaseManager.isYamlMode()) {
            initializeYamlStorage();
        } else {
            initializeDatabaseStorage();
        }

        loadPendingRestores();
        startExpiryChecker();
    }

    private void initializeYamlStorage() {
        File pendingFolder = new File(plugin.getDataFolder(), "pending_restores");
        if (!pendingFolder.exists()) {
            pendingFolder.mkdirs();
        }
    }

    private void initializeDatabaseStorage() {
        try (Connection conn = databaseManager.getConnection()) {
            if (conn == null) return;

            String sql;
            if (configManager.getConfig().getString("database.type", "sqlite").equalsIgnoreCase("mysql")) {
                sql = """
                    CREATE TABLE IF NOT EXISTS invrewind_pending_restores (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        player_uuid VARCHAR(36) NOT NULL UNIQUE,
                        player_name VARCHAR(16) NOT NULL,
                        backup_id INT NOT NULL,
                        backup_type VARCHAR(20) NOT NULL,
                        scheduled_time BIGINT NOT NULL,
                        expiry_time BIGINT NOT NULL,
                        scheduled_by VARCHAR(36) NOT NULL,
                        restore_inventory BOOLEAN NOT NULL,
                        restore_armor BOOLEAN NOT NULL,
                        restore_offhand BOOLEAN NOT NULL,
                        restore_enderchest BOOLEAN NOT NULL,
                        restore_health BOOLEAN NOT NULL,
                        restore_hunger BOOLEAN NOT NULL,
                        restore_xp BOOLEAN NOT NULL,
                        restore_location BOOLEAN NOT NULL,
                        overwrite BOOLEAN NOT NULL DEFAULT FALSE,
                        INDEX idx_player_uuid (player_uuid),
                        INDEX idx_expiry_time (expiry_time)
                    )
                    """;
            } else {
                sql = """
                    CREATE TABLE IF NOT EXISTS invrewind_pending_restores (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        player_uuid TEXT NOT NULL UNIQUE,
                        player_name TEXT NOT NULL,
                        backup_id INTEGER NOT NULL,
                        backup_type TEXT NOT NULL,
                        scheduled_time INTEGER NOT NULL,
                        expiry_time INTEGER NOT NULL,
                        scheduled_by TEXT NOT NULL,
                        restore_inventory INTEGER NOT NULL,
                        restore_armor INTEGER NOT NULL,
                        restore_offhand INTEGER NOT NULL,
                        restore_enderchest INTEGER NOT NULL,
                        restore_health INTEGER NOT NULL,
                        restore_hunger INTEGER NOT NULL,
                        restore_xp INTEGER NOT NULL,
                        restore_location INTEGER NOT NULL,
                        overwrite INTEGER NOT NULL DEFAULT 0
                    )
                    """;
            }

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.execute();
            }

            try {
                String alterSql;
                if (configManager.getConfig().getString("database.type", "sqlite").equalsIgnoreCase("mysql")) {
                    alterSql = "ALTER TABLE invrewind_pending_restores ADD COLUMN IF NOT EXISTS overwrite BOOLEAN NOT NULL DEFAULT FALSE";
                } else {
                    alterSql = "ALTER TABLE invrewind_pending_restores ADD COLUMN overwrite INTEGER NOT NULL DEFAULT 0";
                }
                try (PreparedStatement alterStmt = conn.prepareStatement(alterSql)) {
                    alterStmt.execute();
                } catch (SQLException e) {
                }
            } catch (Exception e) {
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize pending restores table", e);
        }
    }

    private void loadPendingRestores() {
        pendingRestores.clear();

        if (databaseManager.isYamlMode()) {
            loadPendingRestoresFromYaml();
        } else {
            loadPendingRestoresFromDatabase();
        }

        plugin.getLogger().info("Loaded " + pendingRestores.size() + " pending restores");
    }

    private void loadPendingRestoresFromYaml() {
        File pendingFolder = new File(plugin.getDataFolder(), "pending_restores");
        if (!pendingFolder.exists()) return;

        File[] files = pendingFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            try {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                
                UUID playerUuid = UUID.fromString(yaml.getString("player-uuid"));
                String playerName = yaml.getString("player-name");
                int backupId = yaml.getInt("backup-id");
                BackupData.BackupType backupType = BackupData.BackupType.fromKey(yaml.getString("backup-type"));
                long scheduledTime = yaml.getLong("scheduled-time");
                long expiryTime = yaml.getLong("expiry-time");
                String scheduledBy = yaml.getString("scheduled-by");
                
                boolean restoreInventory = yaml.getBoolean("restore-inventory");
                boolean restoreArmor = yaml.getBoolean("restore-armor");
                boolean restoreOffhand = yaml.getBoolean("restore-offhand");
                boolean restoreEnderchest = yaml.getBoolean("restore-enderchest");
                boolean restoreHealth = yaml.getBoolean("restore-health");
                boolean restoreHunger = yaml.getBoolean("restore-hunger");
                boolean restoreXp = yaml.getBoolean("restore-xp");
                boolean restoreLocation = yaml.getBoolean("restore-location");
                boolean overwrite = yaml.getBoolean("overwrite", false);

                PendingRestore pending = new PendingRestore(
                    0, playerUuid, playerName, backupId, backupType, scheduledTime, expiryTime,
                    scheduledBy, restoreInventory, restoreArmor, restoreOffhand, restoreEnderchest,
                    restoreHealth, restoreHunger, restoreXp, restoreLocation, overwrite
                );

                pendingRestores.put(playerUuid, pending);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load pending restore from " + file.getName(), e);
            }
        }
    }

    private void loadPendingRestoresFromDatabase() {
        String sql = "SELECT * FROM invrewind_pending_restores";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                int id = rs.getInt("id");
                UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                String playerName = rs.getString("player_name");
                int backupId = rs.getInt("backup_id");
                BackupData.BackupType backupType = BackupData.BackupType.fromKey(rs.getString("backup_type"));
                long scheduledTime = rs.getLong("scheduled_time");
                long expiryTime = rs.getLong("expiry_time");
                String scheduledBy = rs.getString("scheduled_by");

                boolean restoreInventory = rs.getBoolean("restore_inventory");
                boolean restoreArmor = rs.getBoolean("restore_armor");
                boolean restoreOffhand = rs.getBoolean("restore_offhand");
                boolean restoreEnderchest = rs.getBoolean("restore_enderchest");
                boolean restoreHealth = rs.getBoolean("restore_health");
                boolean restoreHunger = rs.getBoolean("restore_hunger");
                boolean restoreXp = rs.getBoolean("restore_xp");
                boolean restoreLocation = rs.getBoolean("restore_location");
                
                boolean overwrite = false;
                try {
                    overwrite = rs.getBoolean("overwrite");
                } catch (SQLException e) {
                }

                PendingRestore pending = new PendingRestore(
                    id, playerUuid, playerName, backupId, backupType, scheduledTime, expiryTime,
                    scheduledBy, restoreInventory, restoreArmor, restoreOffhand, restoreEnderchest,
                    restoreHealth, restoreHunger, restoreXp, restoreLocation, overwrite
                );

                pendingRestores.put(playerUuid, pending);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load pending restores from database", e);
        }
    }

    public boolean schedulePendingRestore(@NotNull UUID playerUuid, @NotNull String playerName,
                                         int backupId, @NotNull BackupData.BackupType backupType,
                                         @NotNull String scheduledBy, boolean restoreInventory,
                                         boolean restoreArmor, boolean restoreOffhand, boolean restoreEnderchest,
                                         boolean restoreHealth, boolean restoreHunger, boolean restoreXp,
                                         boolean restoreLocation, boolean overwrite) {

        long scheduledTime = System.currentTimeMillis();
        long expiryHours = configManager.getConfig().getLong("offline-restore.expiry-hours", 24);
        long expiryTime = scheduledTime + (expiryHours * 60 * 60 * 1000);

        PendingRestore pending = new PendingRestore(
            0, playerUuid, playerName, backupId, backupType, scheduledTime, expiryTime,
            scheduledBy, restoreInventory, restoreArmor, restoreOffhand, restoreEnderchest,
            restoreHealth, restoreHunger, restoreXp, restoreLocation, overwrite
        );

        if (databaseManager.isYamlMode()) {
            return savePendingRestoreToYaml(pending);
        } else {
            return savePendingRestoreToDatabase(pending);
        }
    }

    private boolean savePendingRestoreToYaml(@NotNull PendingRestore pending) {
        File pendingFolder = new File(plugin.getDataFolder(), "pending_restores");
        if (!pendingFolder.exists()) {
            pendingFolder.mkdirs();
        }

        File file = new File(pendingFolder, pending.getPlayerUuid().toString() + ".yml");

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("player-uuid", pending.getPlayerUuid().toString());
        yaml.set("player-name", pending.getPlayerName());
        yaml.set("backup-id", pending.getBackupId());
        yaml.set("backup-type", pending.getBackupType().getKey());
        yaml.set("scheduled-time", pending.getScheduledTime());
        yaml.set("expiry-time", pending.getExpiryTime());
        yaml.set("scheduled-by", pending.getScheduledBy());
        yaml.set("restore-inventory", pending.isRestoreInventory());
        yaml.set("restore-armor", pending.isRestoreArmor());
        yaml.set("restore-offhand", pending.isRestoreOffhand());
        yaml.set("restore-enderchest", pending.isRestoreEnderchest());
        yaml.set("restore-health", pending.isRestoreHealth());
        yaml.set("restore-hunger", pending.isRestoreHunger());
        yaml.set("restore-xp", pending.isRestoreXp());
        yaml.set("restore-location", pending.isRestoreLocation());
        yaml.set("overwrite", pending.isOverwrite());

        try {
            yaml.save(file);
            pendingRestores.put(pending.getPlayerUuid(), pending);
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save pending restore", e);
            return false;
        }
    }

    private boolean savePendingRestoreToDatabase(@NotNull PendingRestore pending) {
        String deleteSql = "DELETE FROM invrewind_pending_restores WHERE player_uuid = ?";
        String insertSql = """
            INSERT INTO invrewind_pending_restores
            (player_uuid, player_name, backup_id, backup_type, scheduled_time, expiry_time, scheduled_by,
             restore_inventory, restore_armor, restore_offhand, restore_enderchest,
             restore_health, restore_hunger, restore_xp, restore_location, overwrite)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = databaseManager.getConnection()) {
            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                deleteStmt.setString(1, pending.getPlayerUuid().toString());
                deleteStmt.executeUpdate();
            }

            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                insertStmt.setString(1, pending.getPlayerUuid().toString());
                insertStmt.setString(2, pending.getPlayerName());
                insertStmt.setInt(3, pending.getBackupId());
                insertStmt.setString(4, pending.getBackupType().getKey());
                insertStmt.setLong(5, pending.getScheduledTime());
                insertStmt.setLong(6, pending.getExpiryTime());
                insertStmt.setString(7, pending.getScheduledBy());
                insertStmt.setBoolean(8, pending.isRestoreInventory());
                insertStmt.setBoolean(9, pending.isRestoreArmor());
                insertStmt.setBoolean(10, pending.isRestoreOffhand());
                insertStmt.setBoolean(11, pending.isRestoreEnderchest());
                insertStmt.setBoolean(12, pending.isRestoreHealth());
                insertStmt.setBoolean(13, pending.isRestoreHunger());
                insertStmt.setBoolean(14, pending.isRestoreXp());
                insertStmt.setBoolean(15, pending.isRestoreLocation());
                insertStmt.setBoolean(16, pending.isOverwrite());
                insertStmt.executeUpdate();
            }

            pendingRestores.put(pending.getPlayerUuid(), pending);
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save pending restore to database", e);
            return false;
        }
    }

    @Nullable
    public PendingRestore getPendingRestore(@NotNull UUID playerUuid) {
        return pendingRestores.get(playerUuid);
    }

    public boolean hasPendingRestore(@NotNull UUID playerUuid) {
        return pendingRestores.containsKey(playerUuid);
    }

    public void removePendingRestore(@NotNull UUID playerUuid) {
        pendingRestores.remove(playerUuid);

        if (databaseManager.isYamlMode()) {
            File file = new File(plugin.getDataFolder(), "pending_restores/" + playerUuid.toString() + ".yml");
            if (file.exists()) {
                file.delete();
            }
        } else {
            String sql = "DELETE FROM invrewind_pending_restores WHERE player_uuid = ?";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerUuid.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to delete pending restore from database", e);
            }
        }
    }

    public boolean isProcessing(@NotNull UUID playerUuid) {
        return processingPlayers.contains(playerUuid);
    }

    public void setProcessing(@NotNull UUID playerUuid, boolean processing) {
        if (processing) {
            processingPlayers.add(playerUuid);
        } else {
            processingPlayers.remove(playerUuid);
        }
    }

    private void startExpiryChecker() {
        long checkInterval = configManager.getConfig().getLong("offline-restore.expiry-check-interval", 3600) * 20L;

        expiryCheckTask = SchedulerUtil.runTaskTimer(plugin, this::checkExpiredRestores, checkInterval, checkInterval);
    }

    private void checkExpiredRestores() {
        List<UUID> expired = new ArrayList<>();

        for (Map.Entry<UUID, PendingRestore> entry : pendingRestores.entrySet()) {
            if (entry.getValue().isExpired()) {
                expired.add(entry.getKey());
            }
        }

        for (UUID uuid : expired) {
            PendingRestore pending = pendingRestores.get(uuid);
            if (pending != null) {
                notifyAdminExpired(pending);
                removePendingRestore(uuid);
                plugin.getLogger().warning("Pending restore expired for player: " + pending.getPlayerName());
            }
        }
    }

    private void notifyAdminExpired(@NotNull PendingRestore pending) {
        Player admin = Bukkit.getPlayer(pending.getScheduledBy());
        if (admin != null && admin.isOnline()) {
            admin.sendMessage("§cPending restore for " + pending.getPlayerName() + " has expired (not logged in within 24 hours)");
        }
    }

    public void shutdown() {
        if (expiryCheckTask != null) {
            expiryCheckTask.cancel();
        }
        processingPlayers.clear();
    }

    @NotNull
    public Map<UUID, PendingRestore> getAllPendingRestores() {
        return new HashMap<>(pendingRestores);
    }
}
