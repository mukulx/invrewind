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

import dev.mukulx.invrewind.model.BackupData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class GUISession {

    private final UUID viewerUuid;
    private UUID targetUuid;
    private BackupData.BackupType currentType;
    private int currentPage;
    private int playerSelectPage;
    private BackupData currentBackup;

    public GUISession(@NotNull UUID viewerUuid, @Nullable UUID targetUuid) {
        this.viewerUuid = viewerUuid;
        this.targetUuid = targetUuid;
        this.currentPage = 0;
        this.playerSelectPage = 0;
    }

    @NotNull
    public UUID getViewerUuid() {
        return viewerUuid;
    }

    @Nullable
    public UUID getTargetUuid() {
        return targetUuid;
    }

    public void setTargetUuid(@NotNull UUID targetUuid) {
        this.targetUuid = targetUuid;
    }

    @Nullable
    public BackupData.BackupType getCurrentType() {
        return currentType;
    }

    public void setCurrentType(@Nullable BackupData.BackupType currentType) {
        this.currentType = currentType;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(int currentPage) {
        this.currentPage = currentPage;
    }

    public int getPlayerSelectPage() {
        return playerSelectPage;
    }

    public void setPlayerSelectPage(int playerSelectPage) {
        this.playerSelectPage = playerSelectPage;
    }

    @Nullable
    public BackupData getCurrentBackup() {
        return currentBackup;
    }

    public void setCurrentBackup(@Nullable BackupData currentBackup) {
        this.currentBackup = currentBackup;
    }
}
