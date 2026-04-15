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

public class ImportCommand implements CommandExecutor, TabCompleter {

    private final InvRewind plugin;
    private final MigrationManager migrationManager;
    private final MessageManager messageManager;

    public ImportCommand(@NotNull InvRewind plugin, @NotNull MigrationManager migrationManager,
                        @NotNull MessageManager messageManager) {
        this.plugin = plugin;
        this.migrationManager = migrationManager;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                            @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("invrewind.import")) {
            messageManager.sendMessage(sender, "general.no-permission");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cusage: /invrewind import <yaml|sqlite|mysql> <filename>");
            return true;
        }

        String sourceType = args[0].toLowerCase();
        String filename = args[1];

        if (!sourceType.equals("yaml") && !sourceType.equals("sqlite") && !sourceType.equals("mysql")) {
            sender.sendMessage("§cinvalid import type. use: yaml, sqlite, or mysql");
            return true;
        }

        sender.sendMessage("§eimporting from " + sourceType + ": " + filename + "...");
        sender.sendMessage("§ethis may take a while for large backups...");

        migrationManager.importData(sourceType, filename).thenAccept(result -> {
            sender.sendMessage(result);
        });

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (!sender.hasPermission("invrewind.import")) {
            return completions;
        }

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            if ("yaml".startsWith(input)) completions.add("yaml");
            if ("sqlite".startsWith(input)) completions.add("sqlite");
            if ("mysql".startsWith(input)) completions.add("mysql");
        } else if (args.length == 2) {
            String type = args[0].toLowerCase();
            if (type.equals("yaml") || type.equals("sqlite") || type.equals("mysql")) {
                List<String> files = migrationManager.getAvailableImports(type);
                String input = args[1].toLowerCase();
                for (String file : files) {
                    if (file.toLowerCase().startsWith(input)) {
                        completions.add(file);
                    }
                }
            }
        }

        return completions;
    }
}
