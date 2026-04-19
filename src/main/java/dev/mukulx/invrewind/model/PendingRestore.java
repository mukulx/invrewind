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

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class PendingRestore {

    private final int id;
    private final UUID playerUuid;
    private final String playerName;
    private final int backupId;
    private final BackupData.BackupType backupType;
    private final long scheduledTime;
    private final long expiryTime;
    private final String scheduledBy;
    private final boolean restoreInventory;
    private final boolean restoreArmor;
    private final boolean restoreOffhand;
    private final boolean restoreEnderchest;
    private final boolean restoreHealth;
    private final boolean restoreHunger;
    private final boolean restoreXp;
    private final boolean restoreLocation;
    private final boolean overwrite;

    public PendingRestore(int id, @NotNull UUID playerUuid, @NotNull String playerName,
                         int backupId, @NotNull BackupData.BackupType backupType,
                         long scheduledTime, long expiryTime, @NotNull String scheduledBy,
                         boolean restoreInventory, boolean restoreArmor, boolean restoreOffhand,
                         boolean restoreEnderchest, boolean restoreHealth, boolean restoreHunger,
                         boolean restoreXp, boolean restoreLocation, boolean overwrite) {
        this.id = id;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.backupId = backupId;
        this.backupType = backupType;
        this.scheduledTime = scheduledTime;
        this.expiryTime = expiryTime;
        this.scheduledBy = scheduledBy;
        this.restoreInventory = restoreInventory;
        this.restoreArmor = restoreArmor;
        this.restoreOffhand = restoreOffhand;
        this.restoreEnderchest = restoreEnderchest;
        this.restoreHealth = restoreHealth;
        this.restoreHunger = restoreHunger;
        this.restoreXp = restoreXp;
        this.restoreLocation = restoreLocation;
        this.overwrite = overwrite;
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

    public int getBackupId() {
        return backupId;
    }

    @NotNull
    public BackupData.BackupType getBackupType() {
        return backupType;
    }

    public long getScheduledTime() {
        return scheduledTime;
    }

    public long getExpiryTime() {
        return expiryTime;
    }

    @NotNull
    public String getScheduledBy() {
        return scheduledBy;
    }

    public boolean isRestoreInventory() {
        return restoreInventory;
    }

    public boolean isRestoreArmor() {
        return restoreArmor;
    }

    public boolean isRestoreOffhand() {
        return restoreOffhand;
    }

    public boolean isRestoreEnderchest() {
        return restoreEnderchest;
    }

    public boolean isRestoreHealth() {
        return restoreHealth;
    }

    public boolean isRestoreHunger() {
        return restoreHunger;
    }

    public boolean isRestoreXp() {
        return restoreXp;
    }

    public boolean isRestoreLocation() {
        return restoreLocation;
    }

    public boolean isOverwrite() {
        return overwrite;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiryTime;
    }
}
