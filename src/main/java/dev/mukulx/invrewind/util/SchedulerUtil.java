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

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

public class SchedulerUtil {

    private static Boolean isFolia = null;

    public static boolean isFolia() {
        if (isFolia == null) {
            try {
                Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
                isFolia = true;
            } catch (ClassNotFoundException e) {
                isFolia = false;
            }
        }
        return isFolia;
    }

    public static void runTask(@NotNull Plugin plugin, @NotNull Runnable task) {
        if (isFolia()) {
            Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public static void runTaskLater(@NotNull Plugin plugin, @NotNull Runnable task, long delay) {
        if (isFolia()) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> task.run(), delay);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delay);
        }
    }

    public static BukkitTask runTaskTimer(@NotNull Plugin plugin, @NotNull Runnable task, long delay, long period) {
        if (isFolia()) {

            Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduledTask -> task.run(), delay, period);
            return null;
        } else {
            return Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
        }
    }

    public static void runTaskAsynchronously(@NotNull Plugin plugin, @NotNull Runnable task) {
        if (isFolia()) {
            Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    public static BukkitTask runTaskTimerAsynchronously(@NotNull Plugin plugin, @NotNull Runnable task, long delay, long period) {
        if (isFolia()) {

            long delayMs = delay * 50;
            long periodMs = period * 50;
            Bukkit.getAsyncScheduler().runAtFixedRate(plugin, scheduledTask -> task.run(),
                delayMs, periodMs, TimeUnit.MILLISECONDS);
            return null;
        } else {
            return Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delay, period);
        }
    }

    public static void runTaskForEntity(@NotNull Plugin plugin, @NotNull Entity entity, @NotNull Runnable task) {
        if (isFolia()) {
            entity.getScheduler().run(plugin, scheduledTask -> task.run(), null);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public static void runTaskLaterForEntity(@NotNull Plugin plugin, @NotNull Entity entity, @NotNull Runnable task, long delay) {
        if (isFolia()) {
            entity.getScheduler().runDelayed(plugin, scheduledTask -> task.run(), null, delay);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delay);
        }
    }

    public static void runTaskAtLocation(@NotNull Plugin plugin, @NotNull Location location, @NotNull Runnable task) {
        if (isFolia()) {
            Bukkit.getRegionScheduler().run(plugin, location, scheduledTask -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public static void cancelTasks(@NotNull Plugin plugin) {
        if (!isFolia()) {
            Bukkit.getScheduler().cancelTasks(plugin);
        }

    }

    public static String getServerType() {
        return isFolia() ? "Folia" : "Paper";
    }
}
