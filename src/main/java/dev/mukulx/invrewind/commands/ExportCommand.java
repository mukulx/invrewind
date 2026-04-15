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
import dev.mukulx.invrewind.managers.MessageManager;
import dev.mukulx.invrewind.managers.MigrationManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ExportCommand implements CommandExecutor, TabCompleter {

    private final InvRewind plugin;
    private final MigrationManager migrationManager;
    private final MessageManager messageManager;

    public ExportCommand(@NotNull InvRewind plugin, @NotNull MigrationManager migrationManager,
                        @NotNull MessageManager messageManager) {
        this.plugin = plugin;
        this.migrationManager = migrationManager;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                            @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("invrewind.export")) {
            messageManager.sendMessage(sender, "general.no-permission");
            return true;
        }

        if (args.length == 0) {
            String currentType = plugin.getDatabaseManager().getActualDatabaseType();
            sender.sendMessage("§eexporting current database (" + currentType + ")...");
            
            migrationManager.exportData(currentType).thenAccept(result -> {
                sender.sendMessage(result);
            });
            return true;
        }

        if (args.length == 1) {
            String targetType = args[0].toLowerCase();
            
            if (!targetType.equals("yaml") && !targetType.equals("sqlite") && !targetType.equals("mysql")) {
                sender.sendMessage("§cinvalid export type. use: yaml, sqlite, or mysql");
                return true;
            }
            
            sender.sendMessage("§eexporting to " + targetType + " format...");
            
            migrationManager.exportData(targetType).thenAccept(result -> {
                sender.sendMessage(result);
            });
            return true;
        }

        sender.sendMessage("§cusage: /invrewind export [yaml|sqlite|mysql]");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1 && sender.hasPermission("invrewind.export")) {
            completions.add("yaml");
            completions.add("sqlite");
            completions.add("mysql");
        }

        return completions;
    }
}
