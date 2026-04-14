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

import dev.mukulx.invrewind.gui.GUIManager;
import dev.mukulx.invrewind.gui.GUISession;
import dev.mukulx.invrewind.managers.MessageManager;
import dev.mukulx.invrewind.model.BackupData;
import dev.mukulx.invrewind.util.RestoreUtil;
import dev.mukulx.invrewind.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GUIListener implements Listener {

    private final GUIManager guiManager;
    private final MessageManager messageManager;

    public GUIListener(@NotNull GUIManager guiManager) {
        this.guiManager = guiManager;
        this.messageManager = guiManager.getPlugin().getMessageManager();
    }

    @EventHandler
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        Inventory inv = event.getInventory();
        InventoryHolder holder = inv.getHolder();

        if (!(holder instanceof GUIManager.GUIHolder)) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        GUIManager.GUIHolder guiHolder = (GUIManager.GUIHolder) holder;
        GUIManager.GUIType type = guiHolder.getType();
        int slot = event.getSlot();

        if (type == GUIManager.GUIType.BACKUP_DETAIL && slot >= 0 && slot <= 40) {

            return;
        }

        if (type == GUIManager.GUIType.ENDER_CHEST && slot >= 0 && slot <= 26) {

            return;
        }

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        switch (type) {
            case PLAYER_SELECT -> handlePlayerSelect(player, clicked, slot);
            case BACKUP_TYPE -> handleBackupTypeSelect(player, clicked, slot);
            case BACKUP_LIST -> handleBackupListClick(player, clicked, slot);
            case BACKUP_DETAIL -> handleBackupDetailClick(player, clicked, slot);
            case ENDER_CHEST -> handleEnderChestClick(player, clicked, slot);
        }
    }

    @EventHandler
    public void onInventoryDrag(@NotNull InventoryDragEvent event) {
        Inventory inv = event.getInventory();
        InventoryHolder holder = inv.getHolder();

        if (holder instanceof GUIManager.GUIHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(@NotNull InventoryCloseEvent event) {

    }

    private void handlePlayerSelect(@NotNull Player viewer, @NotNull ItemStack clicked, int slot) {
        GUISession session = guiManager.getSession(viewer.getUniqueId());
        if (session == null) {
            session = new GUISession(viewer.getUniqueId(), null);

        }

        if (slot == 45 && clicked.getType() == Material.ARROW) {

            int newPage = Math.max(0, session.getPlayerSelectPage() - 1);
            guiManager.openPlayerSelectGUI(viewer, newPage);
            playSound(viewer, "page-turn");
            return;
        } else if (slot == 53 && clicked.getType() == Material.ARROW) {

            guiManager.openPlayerSelectGUI(viewer, session.getPlayerSelectPage() + 1);
            playSound(viewer, "page-turn");
            return;
        }

        if (clicked.getType() != Material.PLAYER_HEAD) {
            return;
        }

        if (clicked.getItemMeta() instanceof SkullMeta meta) {
            if (meta.getOwningPlayer() != null) {
                Player target = Bukkit.getPlayer(meta.getOwningPlayer().getUniqueId());
                if (target != null) {
                    playSound(viewer, "click-player");
                    guiManager.openBackupTypeGUI(viewer, target.getUniqueId());
                }
            }
        }
    }

    private void handleBackupTypeSelect(@NotNull Player viewer, @NotNull ItemStack clicked, int slot) {
        GUISession session = guiManager.getSession(viewer.getUniqueId());
        if (session == null) return;

        if (slot == 24 && clicked.getType() == Material.NETHER_STAR) {
            Player target = Bukkit.getPlayer(session.getTargetUuid());
            if (target != null && target.isOnline()) {
                playSound(viewer, "click-force-backup");
                guiManager.getPlugin().getBackupManager().createBackup(target, BackupData.BackupType.FORCE).thenAccept(success -> {
                    SchedulerUtil.runTaskForEntity(guiManager.getPlugin(), viewer, () -> {
                        if (success) {
                            messageManager.sendMessage(viewer, "gui.messages.backup-created",
                                Map.of("player", target.getName()));
                        }
                    });
                });
            }
            return;
        }

        BackupData.BackupType type = switch (slot) {
            case 10 -> BackupData.BackupType.DEATH;
            case 12 -> BackupData.BackupType.WORLD_CHANGE;
            case 14 -> BackupData.BackupType.JOIN;
            case 16 -> BackupData.BackupType.QUIT;
            case 20 -> BackupData.BackupType.SCHEDULED;
            case 22 -> BackupData.BackupType.FORCE;
            default -> null;
        };

        if (type != null) {
            playSound(viewer, "click-type");
            guiManager.openBackupListGUI(viewer, type, 0);
        }
    }

    private void handleBackupListClick(@NotNull Player viewer, @NotNull ItemStack clicked, int slot) {
        GUISession session = guiManager.getSession(viewer.getUniqueId());
        if (session == null) return;

        if (slot == 45 && clicked.getType() == Material.ARROW) {

            playSound(viewer, "page-turn");
            int newPage = Math.max(0, session.getCurrentPage() - 1);
            guiManager.openBackupListGUI(viewer, session.getCurrentType(), newPage);
        } else if (slot == 53 && clicked.getType() == Material.ARROW) {

            playSound(viewer, "page-turn");
            guiManager.openBackupListGUI(viewer, session.getCurrentType(), session.getCurrentPage() + 1);
        } else if (slot == 49 && clicked.getType() == Material.BARRIER) {

            playSound(viewer, "click-back");
            guiManager.openBackupTypeGUI(viewer, session.getTargetUuid());
        } else if (slot < 45 && clicked.getType() == Material.CHEST) {

            playSound(viewer, "click-backup");
            String displayName = clicked.getItemMeta().getDisplayName();
            String idStr = displayName.replaceAll("[^0-9]", "");
            try {
                int backupId = Integer.parseInt(idStr);
                guiManager.openBackupDetailGUI(viewer, backupId);
            } catch (NumberFormatException e) {

            }
        }
    }

    private void handleBackupDetailClick(@NotNull Player viewer, @NotNull ItemStack clicked, int slot) {
        GUISession session = guiManager.getSession(viewer.getUniqueId());
        if (session == null || session.getCurrentBackup() == null) return;

        BackupData backup = session.getCurrentBackup();
        Player target = Bukkit.getPlayer(session.getTargetUuid());

        if (target == null || !target.isOnline()) {
            messageManager.sendMessage(viewer, "gui.messages.target-must-be-online");
            viewer.closeInventory();
            return;
        }

        Material type = clicked.getType();

        if (slot == 45 && type == Material.EMERALD) {
            playSound(viewer, "click-restore");
            restoreFullBackup(viewer, target, backup);
            return;
        }

        if (type == Material.CHEST) {
            playSound(viewer, "click-equipment");
            restoreInventoryOnly(viewer, target, backup);
        } else if (type == Material.DIAMOND_CHESTPLATE) {
            playSound(viewer, "click-equipment");
            restoreArmorOnly(viewer, target, backup);
        } else if (type == Material.SHIELD) {
            playSound(viewer, "click-equipment");
            restoreOffhandOnly(viewer, target, backup);
        } else if (type == Material.ENDER_CHEST) {
            playSound(viewer, "click-enderchest");

            guiManager.openEnderChestGUI(viewer, backup.getId());
        } else if (type == Material.EXPERIENCE_BOTTLE) {
            playSound(viewer, "click-xp");
            restoreXPOnly(viewer, target, backup);
        } else if (type == Material.ENDER_PEARL) {
            playSound(viewer, "click-teleport");
            teleportToBackupLocation(viewer, target, backup);
        } else if (type == Material.SHULKER_BOX) {
            playSound(viewer, "click-shulker");
            exportToShulker(viewer, backup);
        }

        else if (slot == 52 && type == Material.ORANGE_DYE) {
            playSound(viewer, "click-restore");
            overwriteCurrent(viewer, target, backup);
        } else if (slot == 53 && type == Material.BARRIER) {
            playSound(viewer, "click-back");
            guiManager.openBackupListGUI(viewer, session.getCurrentType(), session.getCurrentPage());
        }
    }

    private void handleEnderChestClick(@NotNull Player viewer, @NotNull ItemStack clicked, int slot) {
        GUISession session = guiManager.getSession(viewer.getUniqueId());
        if (session == null || session.getCurrentBackup() == null) return;

        BackupData backup = session.getCurrentBackup();
        Player target = Bukkit.getPlayer(session.getTargetUuid());

        if (target == null || !target.isOnline()) {
            messageManager.sendMessage(viewer, "gui.messages.target-must-be-online");
            viewer.closeInventory();
            return;
        }

        switch (slot) {
            case 29 -> {
                playSound(viewer, "click-shulker");
                exportEnderChestToShulker(viewer, backup);
            }
            case 30 -> {
                playSound(viewer, "click-enderchest");
                restoreEnderchest(viewer, target, backup);
            }
            case 31 -> {
                playSound(viewer, "click-restore");
                overwriteEnderChest(viewer, target, backup);
            }
            case 32 -> {
                playSound(viewer, "click-back");
                guiManager.openBackupDetailGUI(viewer, backup.getId());
            }
        }
    }

    private void restoreInventory(@NotNull Player viewer, @NotNull Player target, @NotNull BackupData backup) {
        if (backup.getInventory() != null) {
            target.getInventory().setContents(backup.getInventory());
        }
        if (backup.getArmor() != null) {
            target.getInventory().setArmorContents(backup.getArmor());
        }
        if (backup.getOffhand() != null) {
            target.getInventory().setItemInOffHand(backup.getOffhand());
        }

        messageManager.sendMessage(viewer, "gui.messages.inventory-restored");
        viewer.closeInventory();
    }

    private void restoreEnderchest(@NotNull Player viewer, @NotNull Player target, @NotNull BackupData backup) {
        if (backup.getEnderchest() != null) {
            target.getEnderChest().setContents(backup.getEnderchest());
        }
        messageManager.sendMessage(viewer, "gui.messages.enderchest-restored");
        viewer.closeInventory();
    }

    private void teleportToLocation(@NotNull Player viewer, @NotNull BackupData backup) {
        if (backup.getLocation() != null) {
            viewer.teleport(backup.getLocation());
            messageManager.sendMessage(viewer, "gui.messages.teleported");
        } else {
            messageManager.sendMessage(viewer, "gui.messages.no-location-data");
        }
        viewer.closeInventory();
    }

    private void exportToShulker(@NotNull Player viewer, @NotNull BackupData backup) {
        if (backup.getInventory() == null) {
            messageManager.sendMessage(viewer, "gui.messages.no-inventory-data");
            viewer.closeInventory();
            return;
        }

        List<ItemStack> items = new ArrayList<>();
        for (ItemStack item : backup.getInventory()) {
            if (item != null && item.getType() != Material.AIR) {
                items.add(item.clone());
            }
        }

        if (items.isEmpty()) {
            messageManager.sendMessage(viewer, "gui.messages.no-items-to-export");
            viewer.closeInventory();
            return;
        }

        int shulkersNeeded = (int) Math.ceil(items.size() / 27.0);
        int itemIndex = 0;
        int shulkersCreated = 0;

        for (int shulkerNum = 0; shulkerNum < shulkersNeeded; shulkerNum++) {
            ItemStack shulker = new ItemStack(Material.SHULKER_BOX);
            org.bukkit.inventory.meta.BlockStateMeta meta = (org.bukkit.inventory.meta.BlockStateMeta) shulker.getItemMeta();

            if (meta != null) {
                org.bukkit.block.ShulkerBox shulkerBox = (org.bukkit.block.ShulkerBox) meta.getBlockState();

                int slot = 0;
                while (slot < 27 && itemIndex < items.size()) {
                    shulkerBox.getInventory().setItem(slot, items.get(itemIndex).clone());
                    slot++;
                    itemIndex++;
                }

                meta.setBlockState(shulkerBox);

                if (shulkersNeeded > 1) {
                    meta.setDisplayName("§eBackup #" + backup.getId() + " §7(" + (shulkerNum + 1) + "/" + shulkersNeeded + ")");
                } else {
                    meta.setDisplayName("§eBackup #" + backup.getId());
                }

                shulker.setItemMeta(meta);

                viewer.getInventory().addItem(shulker);
                shulkersCreated++;
            }
        }

        if (shulkersCreated > 1) {
            messageManager.sendMessage(viewer, "gui.messages.shulkers-added",
                Map.of("count", String.valueOf(shulkersCreated)));
        } else {
            messageManager.sendMessage(viewer, "gui.messages.shulker-added");
        }
        viewer.closeInventory();
    }

    private void exportEnderChestToShulker(@NotNull Player viewer, @NotNull BackupData backup) {
        if (backup.getEnderchest() == null) {
            messageManager.sendMessage(viewer, "gui.messages.no-enderchest-data");
            viewer.closeInventory();
            return;
        }

        List<ItemStack> items = new ArrayList<>();
        for (ItemStack item : backup.getEnderchest()) {
            if (item != null && item.getType() != Material.AIR) {
                items.add(item.clone());
            }
        }

        if (items.isEmpty()) {
            messageManager.sendMessage(viewer, "gui.messages.no-items-to-export");
            viewer.closeInventory();
            return;
        }

        ItemStack shulker = new ItemStack(Material.PURPLE_SHULKER_BOX);
        org.bukkit.inventory.meta.BlockStateMeta meta = (org.bukkit.inventory.meta.BlockStateMeta) shulker.getItemMeta();

        if (meta != null) {
            org.bukkit.block.ShulkerBox shulkerBox = (org.bukkit.block.ShulkerBox) meta.getBlockState();

            for (int i = 0; i < items.size() && i < 27; i++) {
                shulkerBox.getInventory().setItem(i, items.get(i).clone());
            }

            meta.setBlockState(shulkerBox);
            meta.setDisplayName("§dEnder Chest - Backup #" + backup.getId());
            shulker.setItemMeta(meta);

            viewer.getInventory().addItem(shulker);
            messageManager.sendMessage(viewer, "gui.messages.enderchest-exported");
        } else {
            messageManager.sendMessage(viewer, "gui.messages.shulker-creation-failed");
        }
        viewer.closeInventory();
    }

    private void overwriteCurrent(@NotNull Player viewer, @NotNull Player target, @NotNull BackupData backup) {
        FileConfiguration config = guiManager.getPlugin().getConfigManager().getConfig();

        boolean hasAnyData = backup.getInventory() != null ||
                            backup.getArmor() != null ||
                            backup.getOffhand() != null ||
                            backup.getEnderchest() != null;

        if (!hasAnyData) {
            messageManager.sendMessage(viewer, "gui.messages.no-inventory-data");
            viewer.closeInventory();
            return;
        }

        if (config.getBoolean("restore.overwrite.inventory", true)) {
            target.getInventory().clear();
            RestoreUtil.restoreInventory(target, backup);
        }
        if (config.getBoolean("restore.overwrite.armor", true)) {
            target.getInventory().setArmorContents(new ItemStack[4]);
            RestoreUtil.restoreArmor(target, backup);
        }
        if (config.getBoolean("restore.overwrite.offhand", true)) {
            target.getInventory().setItemInOffHand(null);
            RestoreUtil.restoreOffhand(target, backup);
        }
        if (config.getBoolean("restore.overwrite.enderchest", false)) {
            target.getEnderChest().clear();
            RestoreUtil.restoreEnderChest(target, backup);
        }
        if (config.getBoolean("restore.overwrite.health", false)) {
            RestoreUtil.restoreHealth(target, backup);
        }
        if (config.getBoolean("restore.overwrite.hunger", false)) {
            RestoreUtil.restoreHunger(target, backup);
        }
        if (config.getBoolean("restore.overwrite.xp", false)) {
            RestoreUtil.restoreXP(target, backup);
        }
        if (config.getBoolean("restore.overwrite.location", false)) {
            RestoreUtil.teleportToLocation(target, backup);
        }

        messageManager.sendMessage(viewer, "gui.messages.full-restored");
        viewer.closeInventory();
    }

    private void overwriteEnderChest(@NotNull Player viewer, @NotNull Player target, @NotNull BackupData backup) {
        target.getEnderChest().clear();
        restoreEnderchest(viewer, target, backup);
    }

    private void restoreFullBackup(@NotNull Player viewer, @NotNull Player target, @NotNull BackupData backup) {
        FileConfiguration config = guiManager.getPlugin().getConfigManager().getConfig();

        if (config.getBoolean("restore.full-restore.inventory", true)) {
            RestoreUtil.restoreInventory(target, backup);
        }
        if (config.getBoolean("restore.full-restore.armor", true)) {
            RestoreUtil.restoreArmor(target, backup);
        }
        if (config.getBoolean("restore.full-restore.offhand", true)) {
            RestoreUtil.restoreOffhand(target, backup);
        }
        if (config.getBoolean("restore.full-restore.enderchest", false)) {
            RestoreUtil.restoreEnderChest(target, backup);
        }
        if (config.getBoolean("restore.full-restore.health", true)) {
            RestoreUtil.restoreHealth(target, backup);
        }
        if (config.getBoolean("restore.full-restore.hunger", true)) {
            RestoreUtil.restoreHunger(target, backup);
        }
        if (config.getBoolean("restore.full-restore.xp", true)) {
            RestoreUtil.restoreXP(target, backup);
        }
        if (config.getBoolean("restore.full-restore.location", false)) {
            RestoreUtil.teleportToLocation(target, backup);
        }

        messageManager.sendMessage(viewer, "gui.messages.full-restored");
        viewer.closeInventory();
    }

    private void restoreInventoryOnly(@NotNull Player viewer, @NotNull Player target, @NotNull BackupData backup) {
        RestoreUtil.restoreInventory(target, backup);
        messageManager.sendMessage(viewer, "gui.messages.inventory-restored");
        viewer.closeInventory();
    }

    private void restoreArmorOnly(@NotNull Player viewer, @NotNull Player target, @NotNull BackupData backup) {
        RestoreUtil.restoreArmor(target, backup);
        messageManager.sendMessage(viewer, "gui.messages.armor-restored");
        viewer.closeInventory();
    }

    private void restoreOffhandOnly(@NotNull Player viewer, @NotNull Player target, @NotNull BackupData backup) {
        RestoreUtil.restoreOffhand(target, backup);
        messageManager.sendMessage(viewer, "gui.messages.offhand-restored");
        viewer.closeInventory();
    }

    private void restoreEnderChestOnly(@NotNull Player viewer, @NotNull Player target, @NotNull BackupData backup) {
        RestoreUtil.restoreEnderChest(target, backup);
        messageManager.sendMessage(viewer, "gui.messages.enderchest-restored");
        viewer.closeInventory();
    }

    private void restoreHealthOnly(@NotNull Player viewer, @NotNull Player target, @NotNull BackupData backup) {
        RestoreUtil.restoreHealth(target, backup);
        messageManager.sendMessage(viewer, "gui.messages.health-restored");
        viewer.closeInventory();
    }

    private void restoreHungerOnly(@NotNull Player viewer, @NotNull Player target, @NotNull BackupData backup) {
        RestoreUtil.restoreHunger(target, backup);
        messageManager.sendMessage(viewer, "gui.messages.hunger-restored");
        viewer.closeInventory();
    }

    private void restoreXPOnly(@NotNull Player viewer, @NotNull Player target, @NotNull BackupData backup) {
        RestoreUtil.restoreXP(target, backup);
        messageManager.sendMessage(viewer, "gui.messages.xp-restored");
        viewer.closeInventory();
    }

    private void teleportToBackupLocation(@NotNull Player viewer, @NotNull Player target, @NotNull BackupData backup) {
        boolean success = RestoreUtil.teleportToLocation(target, backup);
        if (success) {
            messageManager.sendMessage(viewer, "gui.messages.teleported");
        } else {
            messageManager.sendMessage(viewer, "gui.messages.no-location-data");
        }
        viewer.closeInventory();
    }

    private void playSound(@NotNull Player player, @NotNull String soundType) {
        if (!guiManager.getPlugin().getConfigManager().getConfig().getBoolean("gui.sounds-enabled", true)) {
            return;
        }

        Sound sound = switch (soundType) {
            case "click-player" -> Sound.BLOCK_NOTE_BLOCK_PLING;
            case "click-type" -> Sound.UI_BUTTON_CLICK;
            case "click-backup" -> Sound.BLOCK_CHEST_OPEN;
            case "click-restore" -> Sound.ENTITY_PLAYER_LEVELUP;
            case "click-enderchest" -> Sound.BLOCK_ENDER_CHEST_OPEN;
            case "click-shulker" -> Sound.BLOCK_SHULKER_BOX_OPEN;
            case "click-teleport" -> Sound.ENTITY_ENDERMAN_TELEPORT;
            case "click-xp" -> Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
            case "click-equipment" -> Sound.ITEM_ARMOR_EQUIP_DIAMOND;
            case "click-force-backup" -> Sound.BLOCK_BEACON_ACTIVATE;
            case "page-turn" -> Sound.ITEM_BOOK_PAGE_TURN;
            case "click-back" -> Sound.UI_BUTTON_CLICK;
            case "open" -> Sound.UI_BUTTON_CLICK;
            default -> null;
        };

        if (sound != null) {
            player.playSound(player.getLocation(), sound, 0.5f, 1.0f);
        }
    }
}
