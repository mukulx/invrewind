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
import dev.mukulx.invrewind.gui.GUIManager;
import dev.mukulx.invrewind.managers.MessageManager;
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

public class InvRewindCommand implements CommandExecutor, TabCompleter {

    private final InvRewind plugin;
    private final GUIManager guiManager;
    private final MessageManager messageManager;
    private final InvRewindForceCommand forceCommand;

    public InvRewindCommand(@NotNull InvRewind plugin, @NotNull GUIManager guiManager,
                            @NotNull MessageManager messageManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.messageManager = messageManager;
        this.forceCommand = new InvRewindForceCommand(plugin, plugin.getBackupManager(), messageManager);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                            @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }

        if (!player.hasPermission("invrewind.use")) {
            messageManager.sendMessage(player, "general.no-permission");
            return true;
        }

        if (args.length == 0) {

            guiManager.openPlayerSelectGUI(player);
            messageManager.sendMessage(player, "commands.invrewind.opening-gui");
            return true;
        }

        if (args.length == 1) {
            if (args[0].equalsIgnoreCase("reload")) {
                if (!player.hasPermission("invrewind.admin")) {
                    messageManager.sendMessage(player, "general.no-permission");
                    return true;
                }

                plugin.getConfigManager().reload();
                plugin.getScheduledBackupManager().restart();
                messageManager.sendMessage(player, "general.reload-success");
                return true;
            }

            if (args[0].equalsIgnoreCase("forcebackup")) {
                messageManager.sendMessage(player, "commands.forcebackup.usage");
                return true;
            }

            if (!player.hasPermission("invrewind.restore.others")) {
                messageManager.sendMessage(player, "general.no-permission");
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                messageManager.sendMessage(player, "general.player-not-found",
                    Map.of("player", args[0]));
                return true;
            }

            guiManager.openBackupTypeGUI(player, target.getUniqueId());
            messageManager.sendMessage(player, "commands.invrewind.opening-gui");
            return true;
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("forcebackup")) {

            String[] forceArgs = new String[args.length - 1];
            System.arraycopy(args, 1, forceArgs, 0, args.length - 1);
            return forceCommand.onCommand(sender, command, label, forceArgs);
        }

        messageManager.sendMessage(player, "commands.invrewind.usage");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            if (sender.hasPermission("invrewind.admin")) {
                completions.add("reload");
            }

            if (sender.hasPermission("invrewind.forcebackup")) {
                completions.add("forcebackup");
            }

            if (sender.hasPermission("invrewind.restore.others")) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    completions.add(player.getName());
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("forcebackup")) {
            completions.add("all");
            for (Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }
        }

        return completions;
    }
}
