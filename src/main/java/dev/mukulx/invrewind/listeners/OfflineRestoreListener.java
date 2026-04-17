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
import dev.mukulx.invrewind.managers.OfflineRestoreManager;
import dev.mukulx.invrewind.model.BackupData;
import dev.mukulx.invrewind.model.PendingRestore;
import dev.mukulx.invrewind.util.RestoreUtil;
import dev.mukulx.invrewind.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.logging.Level;

public class OfflineRestoreListener implements Listener {

    private final InvRewind plugin;
    private final OfflineRestoreManager offlineRestoreManager;
    private final BackupManager backupManager;
    private final ConfigManager configManager;

    public OfflineRestoreListener(@NotNull InvRewind plugin,
                                  @NotNull OfflineRestoreManager offlineRestoreManager,
                                  @NotNull BackupManager backupManager,
                                  @NotNull ConfigManager configManager) {
        this.plugin = plugin;
        this.offlineRestoreManager = offlineRestoreManager;
        this.backupManager = backupManager;
        this.configManager = configManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!configManager.getConfig().getBoolean("offline-restore.enabled", true)) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        if (!offlineRestoreManager.hasPendingRestore(playerUuid)) {
            return;
        }

        PendingRestore pending = offlineRestoreManager.getPendingRestore(playerUuid);
        if (pending == null) {
            return;
        }

        if (pending.isExpired()) {
            notifyAdminExpired(pending);
            offlineRestoreManager.removePendingRestore(playerUuid);
            plugin.getLogger().warning("pending restore expired for player: " + player.getName());
            return;
        }

        long delayTicks = configManager.getConfig().getLong("offline-restore.apply-delay-ticks", 20L);

        SchedulerUtil.runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }

            executePendingRestore(player, pending);
        }, delayTicks);
    }

    private void executePendingRestore(@NotNull Player player, @NotNull PendingRestore pending) {
        UUID playerUuid = player.getUniqueId();

        if (offlineRestoreManager.isProcessing(playerUuid)) {
            return;
        }

        offlineRestoreManager.setProcessing(playerUuid, true);

        notifyAdminStarting(pending, player);
        plugin.getLogger().info("starting offline restore for player: " + player.getName() + " (backup #" + pending.getBackupId() + ")");

        if (configManager.getConfig().getBoolean("offline-restore.freeze-player", true)) {
            showRestoreTitle(player);
        }

        backupManager.getBackupsByType(playerUuid, pending.getBackupType()).thenAccept(backups -> {
            BackupData backup = null;
            for (BackupData b : backups) {
                if (b.getId() == pending.getBackupId()) {
                    backup = b;
                    break;
                }
            }

            if (backup == null) {
                    player.sendMessage("<gradient:#FF4444:#CC0000>ғᴀɪʟᴇᴅ ᴛᴏ ʀᴇsᴛᴏʀᴇ: ʙᴀᴄᴋᴜᴘ ɴᴏᴛ ғᴏᴜɴᴅ</gradient>");
                    plugin.getLogger().warning("backup #" + pending.getBackupId() + " not found for player: " + player.getName());
                offlineRestoreManager.removePendingRestore(playerUuid);
                offlineRestoreManager.setProcessing(playerUuid, false);
                notifyAdminFailed(pending, "backup not found");
                return;
            }

            BackupData finalBackup = backup;
            SchedulerUtil.runTaskForEntity(plugin, player, () -> {
                try {
                    applyRestore(player, finalBackup, pending);

                    offlineRestoreManager.removePendingRestore(playerUuid);
                    offlineRestoreManager.setProcessing(playerUuid, false);

                    player.sendMessage("<gradient:#00FF88:#00CCAA>ʏᴏᴜʀ ɪɴᴠᴇɴᴛᴏʀʏ ʜᴀs ʙᴇᴇɴ ʀᴇsᴛᴏʀᴇᴅ</gradient>");
                    plugin.getLogger().info("offline restore completed for player: " + player.getName());
                    notifyAdminSuccess(pending, player);

                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "failed to apply offline restore for " + player.getName(), e);
                    player.sendMessage("<gradient:#FF4444:#CC0000>ғᴀɪʟᴇᴅ ᴛᴏ ʀᴇsᴛᴏʀᴇ ɪɴᴠᴇɴᴛᴏʀʏ</gradient>");
                    offlineRestoreManager.setProcessing(playerUuid, false);
                    notifyAdminFailed(pending, "error during restore");
                }
            });
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "failed to load backup for offline restore", ex);
            player.sendMessage("<gradient:#FF4444:#CC0000>ғᴀɪʟᴇᴅ ᴛᴏ ʀᴇsᴛᴏʀᴇ ɪɴᴠᴇɴᴛᴏʀʏ</gradient>");
            offlineRestoreManager.setProcessing(playerUuid, false);
            notifyAdminFailed(pending, "failed to load backup");
            return null;
        });
    }

    private void applyRestore(@NotNull Player player, @NotNull BackupData backup, @NotNull PendingRestore pending) {
        if (pending.isRestoreInventory()) {
            RestoreUtil.restoreInventory(player, backup);
        }
        if (pending.isRestoreArmor()) {
            RestoreUtil.restoreArmor(player, backup);
        }
        if (pending.isRestoreOffhand()) {
            RestoreUtil.restoreOffhand(player, backup);
        }
        if (pending.isRestoreEnderchest()) {
            RestoreUtil.restoreEnderChest(player, backup);
        }
        if (pending.isRestoreHealth()) {
            RestoreUtil.restoreHealth(player, backup);
        }
        if (pending.isRestoreHunger()) {
            RestoreUtil.restoreHunger(player, backup);
        }
        if (pending.isRestoreXp()) {
            RestoreUtil.restoreXP(player, backup);
        }
        if (pending.isRestoreLocation()) {
            RestoreUtil.teleportToLocation(player, backup);
        }
    }

    private void showRestoreTitle(@NotNull Player player) {
        String title = configManager.getConfig().getString("offline-restore.title", "§6restoring inventory...");
        String subtitle = configManager.getConfig().getString("offline-restore.subtitle", "§7please wait");
        int fadeIn = configManager.getConfig().getInt("offline-restore.title-fade-in", 10);
        int stay = configManager.getConfig().getInt("offline-restore.title-stay", 40);
        int fadeOut = configManager.getConfig().getInt("offline-restore.title-fade-out", 10);

        player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
    }

    private void notifyAdminStarting(@NotNull PendingRestore pending, @NotNull Player player) {
        Player admin = Bukkit.getPlayer(pending.getScheduledBy());
        if (admin != null && admin.isOnline()) {
            admin.sendMessage("<gradient:#FFAA00:#FF8800>ᴏғғʟɪɴᴇ ʀᴇsᴛᴏʀᴇ sᴛᴀʀᴛɪɴɢ ғᴏʀ</gradient> <gradient:#00FF88:#00CCAA>" + player.getName() + "</gradient> <gradient:#FFAA00:#FF8800>(ʙᴀᴄᴋᴜᴘ #" + pending.getBackupId() + ")</gradient>");
        }
    }

    private void notifyAdminSuccess(@NotNull PendingRestore pending, @NotNull Player player) {
        Player admin = Bukkit.getPlayer(pending.getScheduledBy());
        if (admin != null && admin.isOnline()) {
            admin.sendMessage("<gradient:#00FF88:#00CCAA>ᴏғғʟɪɴᴇ ʀᴇsᴛᴏʀᴇ ᴄᴏᴍᴘʟᴇᴛᴇᴅ ғᴏʀ</gradient> <gradient:#FFAA00:#FF8800>" + player.getName() + "</gradient>");
        }
    }

    private void notifyAdminFailed(@NotNull PendingRestore pending, @NotNull String reason) {
        Player admin = Bukkit.getPlayer(pending.getScheduledBy());
        if (admin != null && admin.isOnline()) {
            admin.sendMessage("<gradient:#FF4444:#CC0000>ᴏғғʟɪɴᴇ ʀᴇsᴛᴏʀᴇ ғᴀɪʟᴇᴅ ғᴏʀ</gradient> <gradient:#FFAA00:#FF8800>" + pending.getPlayerName() + "</gradient><gradient:#FF4444:#CC0000>: " + reason + "</gradient>");
        }
    }

    private void notifyAdminExpired(@NotNull PendingRestore pending) {
        Player admin = Bukkit.getPlayer(pending.getScheduledBy());
        if (admin != null && admin.isOnline()) {
            long expiryHours = configManager.getConfig().getLong("offline-restore.expiry-hours", 24);
            admin.sendMessage("<gradient:#FF4444:#CC0000>ᴘᴇɴᴅɪɴɢ ʀᴇsᴛᴏʀᴇ ғᴏʀ</gradient> <gradient:#FFAA00:#FF8800>" + pending.getPlayerName() + "</gradient> <gradient:#FF4444:#CC0000>ʜᴀs ᴇxᴘɪʀᴇᴅ (ɴᴏᴛ ʟᴏɢɢᴇᴅ ɪɴ ᴡɪᴛʜɪɴ " + expiryHours + " ʜᴏᴜʀs)</gradient>");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!configManager.getConfig().getBoolean("offline-restore.freeze-player", true)) {
            return;
        }

        Player player = event.getPlayer();
        if (offlineRestoreManager.isProcessing(player.getUniqueId())) {
            if (event.getFrom().getX() != event.getTo().getX() ||
                event.getFrom().getY() != event.getTo().getY() ||
                event.getFrom().getZ() != event.getTo().getZ()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!configManager.getConfig().getBoolean("offline-restore.freeze-player", true)) {
            return;
        }

        if (event.getWhoClicked() instanceof Player player) {
            if (offlineRestoreManager.isProcessing(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!configManager.getConfig().getBoolean("offline-restore.freeze-player", true)) {
            return;
        }

        if (event.getEntity() instanceof Player player) {
            if (offlineRestoreManager.isProcessing(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (!configManager.getConfig().getBoolean("offline-restore.freeze-player", true)) {
            return;
        }

        Player player = event.getPlayer();
        if (offlineRestoreManager.isProcessing(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!configManager.getConfig().getBoolean("offline-restore.freeze-player", true)) {
            return;
        }

        Player player = event.getPlayer();
        if (offlineRestoreManager.isProcessing(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        if (offlineRestoreManager.isProcessing(playerUuid)) {
            offlineRestoreManager.setProcessing(playerUuid, false);
            plugin.getLogger().warning("player " + player.getName() + " quit during offline restore");
        }
    }
}
