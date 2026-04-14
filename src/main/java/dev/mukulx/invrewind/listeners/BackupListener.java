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
package dev.mukulx.invrewind.listeners;

import dev.mukulx.invrewind.InvRewind;
import dev.mukulx.invrewind.config.ConfigManager;
import dev.mukulx.invrewind.managers.BackupManager;
import dev.mukulx.invrewind.managers.ScheduledBackupManager;
import dev.mukulx.invrewind.model.BackupData;
import dev.mukulx.invrewind.util.SchedulerUtil;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jetbrains.annotations.NotNull;

public class BackupListener implements Listener {

    private final InvRewind plugin;
    private final BackupManager backupManager;
    private final ConfigManager configManager;
    private final ScheduledBackupManager scheduledBackupManager;

    public BackupListener(@NotNull InvRewind plugin, @NotNull BackupManager backupManager,
                          @NotNull ConfigManager configManager, @NotNull ScheduledBackupManager scheduledBackupManager) {
        this.plugin = plugin;
        this.backupManager = backupManager;
        this.configManager = configManager;
        this.scheduledBackupManager = scheduledBackupManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDeath(@NotNull PlayerDeathEvent event) {
        if (!configManager.getConfig().getBoolean("auto-backup.on-death", true)) {
            return;
        }

        Player player = event.getEntity();

        backupManager.createBackup(player, BackupData.BackupType.DEATH);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(@NotNull PlayerTeleportEvent event) {
        if (!configManager.getConfig().getBoolean("auto-backup.on-world-change", true)) {
            return;
        }

        Player player = event.getPlayer();

        World fromWorld = event.getFrom().getWorld();
        World toWorld = event.getTo() != null ? event.getTo().getWorld() : null;

        if (toWorld != null && !fromWorld.equals(toWorld)) {

            backupManager.createBackup(player, BackupData.BackupType.WORLD_CHANGE);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();

        scheduledBackupManager.onPlayerJoin(player);

        if (!configManager.getConfig().getBoolean("auto-backup.on-join", true)) {
            return;
        }

        SchedulerUtil.runTaskLaterForEntity(plugin, player, () -> {
            backupManager.createBackup(player, BackupData.BackupType.JOIN);
        }, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();

        scheduledBackupManager.onPlayerQuit(player);

        if (!configManager.getConfig().getBoolean("auto-backup.on-quit", true)) {
            return;
        }

        backupManager.createBackup(player, BackupData.BackupType.QUIT);
    }
}
