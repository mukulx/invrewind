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
import dev.mukulx.invrewind.model.BackupData;
import dev.mukulx.invrewind.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ScheduledBackupManager {

    private final InvRewind plugin;
    private final ConfigManager configManager;
    private final BackupManager backupManager;
    private final MessageManager messageManager;
    private final Map<UUID, PlayerBackupData> playerData;
    private BukkitTask schedulerTask;

    public ScheduledBackupManager(@NotNull InvRewind plugin, @NotNull ConfigManager configManager,
                                  @NotNull BackupManager backupManager, @NotNull MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.backupManager = backupManager;
        this.messageManager = messageManager;
        this.playerData = new HashMap<>();
    }

    public void start() {
        if (!configManager.getConfig().getBoolean("auto-backup.scheduled.enabled", false)) {
            return;
        }

        schedulerTask = SchedulerUtil.runTaskTimerAsynchronously(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                checkAndBackup(player);
            }
        }, 20L * 60L, 20L * 60L);

        plugin.getLogger().info("Scheduled backup system started");
    }

    public void stop() {
        if (schedulerTask != null) {
            schedulerTask.cancel();
            schedulerTask = null;
        }
        playerData.clear();
        plugin.getLogger().info("Scheduled backup system stopped");
    }

    public void restart() {
        stop();
        start();
    }

    private void checkAndBackup(@NotNull Player player) {
        UUID uuid = player.getUniqueId();

        int interval = getBackupInterval(player);
        if (interval <= 0) {
            return;
        }

        PlayerBackupData data = playerData.computeIfAbsent(uuid, k -> new PlayerBackupData());

        long now = System.currentTimeMillis();
        long timeSinceLastBackup = now - data.lastBackupTime;
        long intervalMillis = interval * 60L * 1000L;

        if (timeSinceLastBackup < intervalMillis) {
            return;
        }

        if (configManager.getConfig().getBoolean("auto-backup.scheduled.require-movement", true)) {
            Location currentLoc = player.getLocation();
            if (data.lastLocation != null && isSameLocation(data.lastLocation, currentLoc)) {
                return;
            }
            data.lastLocation = currentLoc.clone();
        }

        int minItems = configManager.getConfig().getInt("auto-backup.scheduled.min-items", 1);
        if (minItems > 0) {
            int itemCount = countItems(player);
            if (itemCount < minItems) {
                return;
            }
        }

        backupManager.createBackup(player, BackupData.BackupType.SCHEDULED).thenAccept(success -> {
            if (success) {
                data.lastBackupTime = now;

                if (configManager.getConfig().getBoolean("auto-backup.scheduled.notify-player", false)) {
                    SchedulerUtil.runTaskForEntity(plugin, player, () -> {
                        messageManager.sendMessage(player, "backup.scheduled-created");
                    });
                }
            }
        });
    }

    private int getBackupInterval(@NotNull Player player) {

        return configManager.getConfig().getInt("auto-backup.scheduled.interval", 30);
    }

    private boolean isSameLocation(@NotNull Location loc1, @NotNull Location loc2) {
        if (!loc1.getWorld().equals(loc2.getWorld())) {
            return false;
        }

        return loc1.distanceSquared(loc2) < 25;
    }

    private int countItems(@NotNull Player player) {
        int count = 0;
        for (org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) {
                count++;
            }
        }
        return count;
    }

    public void onPlayerJoin(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        PlayerBackupData data = playerData.computeIfAbsent(uuid, k -> new PlayerBackupData());
        data.lastLocation = player.getLocation().clone();
    }

    public void onPlayerQuit(@NotNull Player player) {

    }

    private static class PlayerBackupData {
        private long lastBackupTime = 0;
        private Location lastLocation = null;
    }
}
