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
package dev.mukulx.invrewind.model;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class BackupData {

    private final int id;
    private final UUID playerUuid;
    private final String playerName;
    private final BackupType backupType;
    private final long timestamp;
    private final Location location;
    private final double health;
    private final int hunger;
    private final int xpLevel;
    private final float xpProgress;
    private final ItemStack[] inventory;
    private final ItemStack[] armor;
    private final ItemStack offhand;
    private final ItemStack[] enderchest;

    public BackupData(int id, @NotNull UUID playerUuid, @NotNull String playerName,
                      @NotNull BackupType backupType, long timestamp,
                      @Nullable Location location, double health, int hunger,
                      int xpLevel, float xpProgress,
                      @Nullable ItemStack[] inventory, @Nullable ItemStack[] armor,
                      @Nullable ItemStack offhand, @Nullable ItemStack[] enderchest) {
        this.id = id;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.backupType = backupType;
        this.timestamp = timestamp;
        this.location = location;
        this.health = health;
        this.hunger = hunger;
        this.xpLevel = xpLevel;
        this.xpProgress = xpProgress;
        this.inventory = inventory;
        this.armor = armor;
        this.offhand = offhand;
        this.enderchest = enderchest;
    }

    public int getId() {
        return id;
    }

    @NotNull
    public UUID getPlayerUuid() {
        return playerUuid;
    }

    @NotNull
    public String getPlayerName() {
        return playerName;
    }

    @NotNull
    public BackupType getBackupType() {
        return backupType;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Nullable
    public Location getLocation() {
        return location;
    }

    public double getHealth() {
        return health;
    }

    public int getHunger() {
        return hunger;
    }

    public int getXpLevel() {
        return xpLevel;
    }

    public float getXpProgress() {
        return xpProgress;
    }

    @Nullable
    public ItemStack[] getInventory() {
        return inventory;
    }

    @Nullable
    public ItemStack[] getArmor() {
        return armor;
    }

    @Nullable
    public ItemStack getOffhand() {
        return offhand;
    }

    @Nullable
    public ItemStack[] getEnderchest() {
        return enderchest;
    }

    public enum BackupType {
        DEATH("death"),
        WORLD_CHANGE("world-change"),
        JOIN("join"),
        QUIT("quit"),
        SCHEDULED("scheduled"),
        FORCE("force");

        private final String key;

        BackupType(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }

        @NotNull
        public static BackupType fromKey(@NotNull String key) {
            for (BackupType type : values()) {
                if (type.key.equalsIgnoreCase(key)) {
                    return type;
                }
            }
            return FORCE;
        }
    }
}
