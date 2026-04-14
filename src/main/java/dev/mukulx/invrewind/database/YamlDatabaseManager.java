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

import dev.mukulx.invrewind.InvRewind;
import dev.mukulx.invrewind.model.BackupData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class YamlDatabaseManager {

    private final InvRewind plugin;
    private final File backupsFolder;
    private final boolean prettyPrint;
    private final Map<String, Integer> nextIdMap;

    public YamlDatabaseManager(@NotNull InvRewind plugin) {
        this.plugin = plugin;
        String folderName = plugin.getConfigManager().getConfig().getString("database.yaml.folder", "backups");
        this.backupsFolder = new File(plugin.getDataFolder(), folderName);
        this.prettyPrint = plugin.getConfigManager().getConfig().getBoolean("database.yaml.pretty-print", true);
        this.nextIdMap = new HashMap<>();

        createFolderStructure();
    }

    private void createFolderStructure() {
        if (!backupsFolder.exists()) {
            backupsFolder.mkdirs();
        }

        for (BackupData.BackupType type : BackupData.BackupType.values()) {
            File typeFolder = new File(backupsFolder, type.getKey());
            if (!typeFolder.exists()) {
                typeFolder.mkdirs();
            }
        }

        plugin.getLogger().info("YAML backup storage initialized at: " + backupsFolder.getPath());
    }

    @NotNull
    public File getPlayerFolder(@NotNull UUID playerUuid, @NotNull String playerName, @NotNull BackupData.BackupType type) {
        File typeFolder = new File(backupsFolder, type.getKey());
        File playerFolder = new File(typeFolder, playerName);

        if (!playerFolder.exists()) {
            playerFolder.mkdirs();
        }

        return playerFolder;
    }

    public int getNextId(@NotNull UUID playerUuid, @NotNull BackupData.BackupType type) {
        String key = playerUuid.toString() + "_" + type.getKey();

        if (!nextIdMap.containsKey(key)) {

            int maxId = 0;
            File playerFolder = getPlayerFolder(playerUuid, "temp", type);

            if (playerFolder.exists()) {
                File[] files = playerFolder.listFiles((dir, name) -> name.startsWith("backup_") && name.endsWith(".yml"));
                if (files != null) {
                    for (File file : files) {
                        try {
                            String idStr = file.getName().replace("backup_", "").replace(".yml", "");
                            int id = Integer.parseInt(idStr);
                            if (id > maxId) {
                                maxId = id;
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }

            nextIdMap.put(key, maxId + 1);
        }

        int id = nextIdMap.get(key);
        nextIdMap.put(key, id + 1);
        return id;
    }

    public boolean saveBackup(@NotNull BackupData backup) {
        try {
            File playerFolder = getPlayerFolder(backup.getPlayerUuid(), backup.getPlayerName(), backup.getBackupType());
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
            if (backup.getOffhand() != null && backup.getOffhand().getType() != org.bukkit.Material.AIR) {
                yaml.set("offhand", backup.getOffhand());
            }
            if (backup.getEnderchest() != null) {
                yaml.set("enderchest", Arrays.asList(backup.getEnderchest()));
            }

            yaml.save(backupFile);
            return true;

        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save YAML backup", e);
            return false;
        }
    }

    @Nullable
    public BackupData loadBackup(@NotNull File backupFile) {
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
                World world = Bukkit.getWorld(worldName);
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

            ItemStack[] inventory = null;
            ItemStack[] armor = null;
            ItemStack offhand = null;
            ItemStack[] enderchest = null;

            if (yaml.contains("inventory")) {
                List<?> invList = yaml.getList("inventory");
                if (invList != null) {
                    inventory = invList.toArray(new ItemStack[0]);
                }
            }

            if (yaml.contains("armor")) {
                List<?> armorList = yaml.getList("armor");
                if (armorList != null) {
                    armor = armorList.toArray(new ItemStack[0]);
                }
            }

            if (yaml.contains("offhand")) {
                offhand = yaml.getItemStack("offhand");
            }

            if (yaml.contains("enderchest")) {
                List<?> ecList = yaml.getList("enderchest");
                if (ecList != null) {
                    enderchest = ecList.toArray(new ItemStack[0]);
                }
            }

            return new BackupData(id, playerUuid, playerName, type, timestamp, location,
                                 health, hunger, xpLevel, xpProgress, inventory, armor, offhand, enderchest);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load backup from " + backupFile.getName(), e);
            return null;
        }
    }

    @NotNull
    public List<BackupData> getBackups(@NotNull UUID playerUuid, @NotNull String playerName) {
        List<BackupData> backups = new ArrayList<>();

        for (BackupData.BackupType type : BackupData.BackupType.values()) {
            backups.addAll(getBackupsByType(playerUuid, playerName, type));
        }

        backups.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));

        return backups;
    }

    @NotNull
    public List<BackupData> getBackupsByType(@NotNull UUID playerUuid, @NotNull String playerName, @NotNull BackupData.BackupType type) {
        List<BackupData> backups = new ArrayList<>();
        File playerFolder = getPlayerFolder(playerUuid, playerName, type);

        if (!playerFolder.exists()) {
            return backups;
        }

        File[] files = playerFolder.listFiles((dir, name) -> name.startsWith("backup_") && name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                BackupData backup = loadBackup(file);
                if (backup != null) {
                    backups.add(backup);
                }
            }
        }

        backups.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));

        return backups;
    }

    @Nullable
    public BackupData getBackup(int backupId, @NotNull UUID playerUuid, @NotNull String playerName, @NotNull BackupData.BackupType type) {
        File playerFolder = getPlayerFolder(playerUuid, playerName, type);
        File backupFile = new File(playerFolder, "backup_" + backupId + ".yml");

        if (!backupFile.exists()) {
            return null;
        }

        return loadBackup(backupFile);
    }

    public boolean deleteBackup(int backupId, @NotNull UUID playerUuid, @NotNull String playerName, @NotNull BackupData.BackupType type) {
        File playerFolder = getPlayerFolder(playerUuid, playerName, type);
        File backupFile = new File(playerFolder, "backup_" + backupId + ".yml");

        if (backupFile.exists()) {
            return backupFile.delete();
        }

        return false;
    }

    public void cleanupOldBackups(@NotNull UUID playerUuid, @NotNull String playerName, @NotNull BackupData.BackupType type, int maxBackups) {
        if (maxBackups == -1) {
            return;
        }

        List<BackupData> backups = getBackupsByType(playerUuid, playerName, type);

        if (backups.size() > maxBackups) {

            for (int i = maxBackups; i < backups.size(); i++) {
                BackupData backup = backups.get(i);
                deleteBackup(backup.getId(), playerUuid, playerName, type);
            }
        }
    }

    public void close() {

        plugin.getLogger().info("YAML database manager closed");
    }
}
