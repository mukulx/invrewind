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
package dev.mukulx.invrewind.util;

import dev.mukulx.invrewind.model.BackupData;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class RestoreUtil {

    public static void restoreInventory(@NotNull Player player, @NotNull BackupData backup) {
        ItemStack[] inventory = backup.getInventory();
        if (inventory != null) {
            player.getInventory().setContents(inventory);
        }
    }

    public static void addInventoryItems(@NotNull Player player, @NotNull BackupData backup) {
        ItemStack[] inventory = backup.getInventory();
        if (inventory != null) {
            for (ItemStack item : inventory) {
                if (item != null && item.getType() != org.bukkit.Material.AIR) {
                    java.util.HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
                    if (!leftover.isEmpty()) {
                        for (ItemStack drop : leftover.values()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), drop);
                        }
                    }
                }
            }
        }
    }

    public static void restoreArmor(@NotNull Player player, @NotNull BackupData backup) {
        ItemStack[] armor = backup.getArmor();
        if (armor != null) {
            player.getInventory().setArmorContents(armor);
        }
    }

    public static void restoreOffhand(@NotNull Player player, @NotNull BackupData backup) {
        ItemStack offhand = backup.getOffhand();
        if (offhand != null) {
            player.getInventory().setItemInOffHand(offhand);
        }
    }

    public static void restoreEnderChest(@NotNull Player player, @NotNull BackupData backup) {
        ItemStack[] enderchest = backup.getEnderchest();
        if (enderchest != null) {
            player.getEnderChest().setContents(enderchest);
        }
    }

    public static void restoreHealth(@NotNull Player player, @NotNull BackupData backup) {
        double health = backup.getHealth();
        double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();

        if (health > 0 && health <= maxHealth) {
            player.setHealth(health);
        } else if (health > maxHealth) {
            player.setHealth(maxHealth);
        }
    }

    public static void restoreHunger(@NotNull Player player, @NotNull BackupData backup) {
        int hunger = backup.getHunger();
        if (hunger >= 0 && hunger <= 20) {
            player.setFoodLevel(hunger);
        }
    }

    public static void restoreXP(@NotNull Player player, @NotNull BackupData backup) {
        int xpLevel = backup.getXpLevel();
        float xpProgress = backup.getXpProgress();

        if (xpLevel >= 0) {
            player.setLevel(xpLevel);
        }
        if (xpProgress >= 0 && xpProgress <= 1) {
            player.setExp(xpProgress);
        }
    }

    public static boolean teleportToLocation(@NotNull Player player, @NotNull BackupData backup) {
        Location location = backup.getLocation();
        if (location != null && location.getWorld() != null) {
            if (SchedulerUtil.isFolia()) {
                player.teleportAsync(location);
            } else {
                player.teleport(location);
            }
            return true;
        }
        return false;
    }
}
