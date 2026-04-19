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
package dev.mukulx.invrewind.gui;

import dev.mukulx.invrewind.InvRewind;
import dev.mukulx.invrewind.config.ConfigManager;
import dev.mukulx.invrewind.managers.BackupManager;
import dev.mukulx.invrewind.managers.MessageManager;
import dev.mukulx.invrewind.model.BackupData;
import dev.mukulx.invrewind.util.SchedulerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.*;

public class GUIManager {

    private final InvRewind plugin;
    private final BackupManager backupManager;
    private final MessageManager messageManager;
    private final ConfigManager configManager;
    private final Map<UUID, GUISession> sessions;

    public GUIManager(@NotNull InvRewind plugin, @NotNull BackupManager backupManager,
                      @NotNull MessageManager messageManager, @NotNull ConfigManager configManager) {
        this.plugin = plugin;
        this.backupManager = backupManager;
        this.messageManager = messageManager;
        this.configManager = configManager;
        this.sessions = new HashMap<>();
    }

    public void openPlayerSelectGUI(@NotNull Player viewer) {
        openPlayerSelectGUI(viewer, 0);
    }

    public void openPlayerSelectGUI(@NotNull Player viewer, int page) {
        GUISession session = sessions.computeIfAbsent(viewer.getUniqueId(), k -> new GUISession(viewer.getUniqueId(), null));
        session.setCurrentPage(page);
        
        backupManager.getAllPlayersWithBackups().thenAccept(allPlayers -> {
            SchedulerUtil.runTask(plugin, () -> {
                Component title = messageManager.getMessage("gui.titles.select-player");
                Inventory inv = Bukkit.createInventory(new GUIHolder(GUIType.PLAYER_SELECT), 54, title);

                int itemsPerPage = 45;
                int startIndex = page * itemsPerPage;
                int endIndex = Math.min(startIndex + itemsPerPage, allPlayers.size());

                int slot = 0;
                for (int i = startIndex; i < endIndex; i++) {
                    var playerInfo = allPlayers.get(i);
                    UUID uuid = playerInfo.getKey();
                    String name = playerInfo.getValue();

                    ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
                    SkullMeta meta = (SkullMeta) skull.getItemMeta();
                    if (meta != null) {
                        meta.displayName(messageManager.toSmallCaps(name)
                            .color(NamedTextColor.YELLOW));
                        meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
                        
                        Player onlinePlayer = Bukkit.getPlayer(uuid);
                        if (onlinePlayer != null && onlinePlayer.isOnline()) {
                            meta.lore(Arrays.asList(
                                messageManager.getMessage("gui.lore.click-to-view"),
                                messageManager.toSmallCaps("status: online").color(NamedTextColor.GREEN)
                            ));
                        } else {
                            meta.lore(Arrays.asList(
                                messageManager.getMessage("gui.lore.click-to-view"),
                                messageManager.toSmallCaps("status: offline").color(NamedTextColor.RED)
                            ));
                        }
                        skull.setItemMeta(meta);
                    }

                    inv.setItem(slot++, skull);
                }

                if (page > 0) {
                    inv.setItem(45, createNavItem(Material.ARROW, "gui.previous-page"));
                }

                if (endIndex < allPlayers.size()) {
                    inv.setItem(53, createNavItem(Material.ARROW, "gui.next-page"));
                }

                playSound(viewer, "open");
                viewer.openInventory(inv);
            });
        });
    }

    public void openBackupTypeGUI(@NotNull Player viewer, @NotNull UUID targetUuid) {
        Component title = messageManager.getMessage("gui.titles.select-backup-type");

        Inventory inv = Bukkit.createInventory(new GUIHolder(GUIType.BACKUP_TYPE), 27, title);

        GUISession session = new GUISession(viewer.getUniqueId(), targetUuid);
        sessions.put(viewer.getUniqueId(), session);

        inv.setItem(10, createTypeItem(Material.SKELETON_SKULL, "gui.type-names.death", BackupData.BackupType.DEATH));

        inv.setItem(12, createTypeItem(Material.COMPASS, "gui.type-names.world-change", BackupData.BackupType.WORLD_CHANGE));

        inv.setItem(14, createTypeItem(Material.OAK_DOOR, "gui.type-names.join", BackupData.BackupType.JOIN));

        inv.setItem(16, createTypeItem(Material.BARRIER, "gui.type-names.quit", BackupData.BackupType.QUIT));

        inv.setItem(20, createTypeItem(Material.CLOCK, "gui.type-names.scheduled", BackupData.BackupType.SCHEDULED));

        inv.setItem(22, createTypeItem(Material.NETHER_STAR, "gui.type-names.force", BackupData.BackupType.FORCE));

        inv.setItem(24, createForceBackupItem(targetUuid));

        playSound(viewer, "open");

        viewer.openInventory(inv);
    }

    private ItemStack createTypeItem(@NotNull Material material, @NotNull String messageKey, @NotNull BackupData.BackupType type) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(messageManager.getMessage(messageKey));
            meta.lore(Arrays.asList(messageManager.getMessage("gui.lore.click-to-view")));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createForceBackupItem(@NotNull UUID targetUuid) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(messageManager.getMessage("gui.buttons.force-backup"));
            meta.lore(Arrays.asList(messageManager.getMessage("gui.lore.click-to-backup")));
            item.setItemMeta(meta);
        }
        return item;
    }

    public void openBackupListGUI(@NotNull Player viewer, @NotNull BackupData.BackupType type, int page) {
        GUISession session = sessions.get(viewer.getUniqueId());
        if (session == null) return;

        session.setCurrentType(type);
        session.setCurrentPage(page);

        backupManager.getBackupsByType(session.getTargetUuid(), type).thenAccept(backups -> {
            SchedulerUtil.runTask(plugin, () -> {
                Component title = messageManager.getMessage("gui.titles.select-backup");

                Inventory inv = Bukkit.createInventory(new GUIHolder(GUIType.BACKUP_LIST), 54, title);

                int itemsPerPage = 45;
                int startIndex = page * itemsPerPage;
                int endIndex = Math.min(startIndex + itemsPerPage, backups.size());

                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

                for (int i = startIndex; i < endIndex; i++) {
                    BackupData backup = backups.get(i);
                    ItemStack item = new ItemStack(Material.CHEST);
                    ItemMeta meta = item.getItemMeta();

                    if (meta != null) {
                        meta.displayName(messageManager.toSmallCaps("Backup #" + backup.getId())
                            .color(NamedTextColor.YELLOW));

                        List<Component> lore = new ArrayList<>();
                        lore.add(messageManager.toSmallCaps("Time: " + dateFormat.format(new Date(backup.getTimestamp())))
                            .color(NamedTextColor.GRAY));
                        lore.add(messageManager.toSmallCaps("Type: " + backup.getBackupType().getKey())
                            .color(NamedTextColor.GRAY));

                        if (backup.getLocation() != null) {
                            lore.add(messageManager.toSmallCaps("World: " + backup.getLocation().getWorld().getName())
                                .color(NamedTextColor.GRAY));
                            lore.add(messageManager.toSmallCaps(String.format("Location: %d, %d, %d",
                                (int)backup.getLocation().getX(),
                                (int)backup.getLocation().getY(),
                                (int)backup.getLocation().getZ()))
                                .color(NamedTextColor.GRAY));
                        }

                        lore.add(messageManager.toSmallCaps(String.format("Health: %.1f", backup.getHealth()))
                            .color(NamedTextColor.RED));
                        lore.add(messageManager.toSmallCaps("Hunger: " + backup.getHunger())
                            .color(NamedTextColor.GOLD));
                        lore.add(messageManager.toSmallCaps("XP: " + backup.getXpLevel() + " levels")
                            .color(NamedTextColor.GREEN));
                        lore.add(Component.empty());
                        lore.add(messageManager.getMessage("gui.lore.click-to-view-details"));

                        meta.lore(lore);
                        item.setItemMeta(meta);
                    }

                    inv.setItem(i - startIndex, item);
                }

                if (page > 0) {
                    inv.setItem(45, createNavItem(Material.ARROW, "gui.previous-page"));
                }

                if (endIndex < backups.size()) {
                    inv.setItem(53, createNavItem(Material.ARROW, "gui.next-page"));
                }

                inv.setItem(49, createNavItem(Material.BARRIER, "gui.back"));

                viewer.openInventory(inv);
            });
        });
    }

    public void openBackupDetailGUI(@NotNull Player viewer, int backupId) {
        GUISession session = sessions.get(viewer.getUniqueId());
        if (session == null) return;

        BackupData backup = session.getCurrentBackup();
        if (backup == null || backup.getId() != backupId) {

            UUID targetUuid = session.getTargetUuid();
            BackupData.BackupType type = session.getCurrentType();

            if (type == null) {
                plugin.getLogger().warning("Cannot fetch backup without type information");
                return;
            }

            backupManager.getBackupsByType(targetUuid, type).thenAccept(backups -> {
                BackupData foundBackup = backups.stream()
                    .filter(b -> b.getId() == backupId)
                    .findFirst()
                    .orElse(null);

                if (foundBackup != null) {
                    session.setCurrentBackup(foundBackup);
                    openBackupDetailGUIWithData(viewer, foundBackup);
                }
            });
        } else {
            openBackupDetailGUIWithData(viewer, backup);
        }
    }

    private void openBackupDetailGUIWithData(@NotNull Player viewer, @NotNull BackupData backup) {
        SchedulerUtil.runTask(plugin, () -> {
            String titleKey = messageManager.getRawMessage("gui.titles.backup-detail");
            titleKey = titleKey.replace("{id}", String.valueOf(backup.getId()));
            Component title = messageManager.parseMessage(titleKey);

            Inventory inv = Bukkit.createInventory(new GUIHolder(GUIType.BACKUP_DETAIL), 54, title);

            ItemStack[] inventory = backup.getInventory();
            if (inventory != null) {
                for (int i = 0; i < Math.min(36, inventory.length); i++) {
                    if (inventory[i] != null) {
                        inv.setItem(i, inventory[i].clone());
                    }
                }
            }

            ItemStack[] armor = backup.getArmor();
            if (armor != null) {
                for (int i = 0; i < Math.min(4, armor.length); i++) {
                    if (armor[i] != null) {
                        inv.setItem(36 + i, armor[i].clone());
                    }
                }
            }

            ItemStack offhand = backup.getOffhand();
            if (offhand != null) {
                inv.setItem(40, offhand.clone());
            }

            int slot = 45;

            inv.setItem(slot++, createActionItem(Material.EMERALD, "gui.buttons.restore-full", 
                List.of("gui.lore.add-items-info", "gui.lore.add-items-overflow")));

            if (configManager.getConfig().getBoolean("restore.buttons.restore-enderchest", true)) {
                inv.setItem(slot++, createActionItem(Material.ENDER_CHEST, "gui.buttons.view-enderchest"));
            }

            if (configManager.getConfig().getBoolean("restore.buttons.restore-xp", true)) {
                inv.setItem(slot++, createActionItem(Material.EXPERIENCE_BOTTLE, "gui.buttons.restore-xp-only"));
            }

            if (configManager.getConfig().getBoolean("restore.buttons.teleport-location", true)) {
                inv.setItem(slot++, createActionItem(Material.ENDER_PEARL, "gui.buttons.teleport-location"));
            }

            if (slot <= 50) {
                inv.setItem(slot++, createActionItem(Material.SHULKER_BOX, "gui.buttons.export-shulker"));
            }

            inv.setItem(52, createActionItem(Material.ORANGE_DYE, "gui.buttons.overwrite-current",
                List.of("gui.lore.overwrite-info")));
            inv.setItem(53, createNavItem(Material.BARRIER, "gui.back"));

            viewer.openInventory(inv);
        });
    }

    public void openEnderChestGUI(@NotNull Player viewer, int backupId) {
        GUISession session = sessions.get(viewer.getUniqueId());
        if (session == null) return;

        BackupData backup = session.getCurrentBackup();
        if (backup == null || backup.getId() != backupId) {

            UUID targetUuid = session.getTargetUuid();
            BackupData.BackupType type = session.getCurrentType();

            if (type == null) {
                plugin.getLogger().warning("Cannot fetch backup without type information");
                return;
            }

            backupManager.getBackupsByType(targetUuid, type).thenAccept(backups -> {
                BackupData foundBackup = backups.stream()
                    .filter(b -> b.getId() == backupId)
                    .findFirst()
                    .orElse(null);

                if (foundBackup != null) {
                    session.setCurrentBackup(foundBackup);
                    openEnderChestGUIWithData(viewer, foundBackup);
                }
            });
        } else {
            openEnderChestGUIWithData(viewer, backup);
        }
    }

    private void openEnderChestGUIWithData(@NotNull Player viewer, @NotNull BackupData backup) {
        SchedulerUtil.runTask(plugin, () -> {
            String titleKey = messageManager.getRawMessage("gui.titles.ender-chest");
            titleKey = titleKey.replace("{id}", String.valueOf(backup.getId()));
            Component title = messageManager.parseMessage(titleKey);

            Inventory inv = Bukkit.createInventory(new GUIHolder(GUIType.ENDER_CHEST), 36, title);

            ItemStack[] enderChest = backup.getEnderchest();
            if (enderChest != null) {
                for (int i = 0; i < Math.min(27, enderChest.length); i++) {
                    if (enderChest[i] != null) {
                        inv.setItem(i, enderChest[i].clone());
                    }
                }
            }

            inv.setItem(29, createActionItem(Material.SHULKER_BOX, "gui.buttons.export-shulker"));
            inv.setItem(30, createActionItem(Material.ENDER_CHEST, "gui.buttons.restore-enderchest"));
            inv.setItem(31, createActionItem(Material.ORANGE_DYE, "gui.buttons.overwrite-current"));
            inv.setItem(32, createNavItem(Material.BARRIER, "gui.back"));

            viewer.openInventory(inv);
        });
    }

    private ItemStack createActionItem(@NotNull Material material, @NotNull String messageKey) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(messageManager.getMessage(messageKey));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createActionItem(@NotNull Material material, @NotNull String messageKey, @NotNull List<String> loreKeys) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(messageManager.getMessage(messageKey));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            for (String loreKey : loreKeys) {
                lore.add(messageManager.getMessage(loreKey));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createNavItem(@NotNull Material material, @NotNull String messageKey) {
        return createActionItem(material, messageKey);
    }

    @Nullable
    public GUISession getSession(@NotNull UUID uuid) {
        return sessions.get(uuid);
    }

    public void removeSession(@NotNull UUID uuid) {
        sessions.remove(uuid);
    }

    @NotNull
    public InvRewind getPlugin() {
        return plugin;
    }

    public void playSound(@NotNull Player player, @NotNull String soundKey) {
        if (!configManager.getConfig().getBoolean("gui.sounds-enabled", true)) {
            return;
        }

        Sound sound = switch (soundKey) {
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

    public static class GUIHolder implements InventoryHolder {
        private final GUIType type;

        public GUIHolder(@NotNull GUIType type) {
            this.type = type;
        }

        @NotNull
        public GUIType getType() {
            return type;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return null;
        }
    }

    public enum GUIType {
        PLAYER_SELECT,
        BACKUP_TYPE,
        BACKUP_LIST,
        BACKUP_DETAIL,
        ENDER_CHEST
    }
}
