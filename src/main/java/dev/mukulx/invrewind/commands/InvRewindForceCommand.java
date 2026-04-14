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
package dev.mukulx.invrewind.commands;

import dev.mukulx.invrewind.InvRewind;
import dev.mukulx.invrewind.managers.BackupManager;
import dev.mukulx.invrewind.managers.MessageManager;
import dev.mukulx.invrewind.model.BackupData;
import dev.mukulx.invrewind.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class InvRewindForceCommand implements CommandExecutor, TabCompleter {

    private final InvRewind plugin;
    private final BackupManager backupManager;
    private final MessageManager messageManager;

    public InvRewindForceCommand(@NotNull InvRewind plugin, @NotNull BackupManager backupManager,
                                 @NotNull MessageManager messageManager) {
        this.plugin = plugin;
        this.backupManager = backupManager;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                            @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("invrewind.forcebackup")) {
            messageManager.sendMessage(sender, "general.no-permission");
            return true;
        }

        if (args.length == 0) {
            messageManager.sendMessage(sender, "commands.forcebackup.usage");
            return true;
        }

        if (args[0].equalsIgnoreCase("all")) {
            messageManager.sendMessage(sender, "commands.forcebackup.backing-up-all");

            AtomicInteger count = new AtomicInteger(0);
            List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());

            for (Player player : players) {
                backupManager.createBackup(player, BackupData.BackupType.FORCE).thenAccept(success -> {
                    if (success) {
                        count.incrementAndGet();
                    }

                    if (count.get() + (players.size() - count.get()) == players.size()) {
                        SchedulerUtil.runTask(plugin, () -> {
                            messageManager.sendMessage(sender, "commands.forcebackup.backed-up-all",
                                Map.of("count", String.valueOf(count.get())));
                        });
                    }
                });
            }

            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            messageManager.sendMessage(sender, "general.player-not-found",
                Map.of("player", args[0]));
            return true;
        }

        messageManager.sendMessage(sender, "commands.forcebackup.backing-up-player",
            Map.of("player", target.getName()));

        backupManager.createBackup(target, BackupData.BackupType.FORCE).thenAccept(success -> {
            SchedulerUtil.runTask(plugin, () -> {
                if (success) {
                    messageManager.sendMessage(sender, "commands.forcebackup.backed-up-player",
                        Map.of("player", target.getName()));
                } else {
                    messageManager.sendMessage(sender, "commands.forcebackup.failed",
                        Map.of("player", target.getName()));
                }
            });
        });

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("all");
            for (Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }
        }

        return completions;
    }
}
