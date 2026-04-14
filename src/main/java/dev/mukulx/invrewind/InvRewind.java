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
package dev.mukulx.invrewind;

import dev.mukulx.invrewind.commands.InvRewindCommand;
import dev.mukulx.invrewind.commands.InvRewindForceCommand;
import dev.mukulx.invrewind.config.ConfigManager;
import dev.mukulx.invrewind.database.DatabaseManager;
import dev.mukulx.invrewind.gui.GUIManager;
import dev.mukulx.invrewind.listeners.BackupListener;
import dev.mukulx.invrewind.listeners.GUIListener;
import dev.mukulx.invrewind.managers.BackupManager;
import dev.mukulx.invrewind.managers.MessageManager;
import dev.mukulx.invrewind.managers.ScheduledBackupManager;
import dev.mukulx.invrewind.util.SchedulerUtil;
import dev.mukulx.invrewind.util.UpdateChecker;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Level;

public final class InvRewind extends JavaPlugin {

    private ConfigManager configManager;
    private MessageManager messageManager;
    private DatabaseManager databaseManager;
    private BackupManager backupManager;
    private GUIManager guiManager;
    private ScheduledBackupManager scheduledBackupManager;

    @Override
    public void onEnable() {
        try {
            displayStartupLogo();

            configManager = new ConfigManager(this);
            configManager.loadConfigs();

            messageManager = new MessageManager(this, configManager);

            databaseManager = new DatabaseManager(this, configManager);
            if (!databaseManager.initialize()) {
                getLogger().severe("Failed to initialize database! Disabling plugin...");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            backupManager = new BackupManager(this, databaseManager, configManager);
            guiManager = new GUIManager(this, backupManager, messageManager, configManager);
            scheduledBackupManager = new ScheduledBackupManager(this, configManager, backupManager, messageManager);

            registerCommands();
            registerListeners();

            scheduledBackupManager.start();
            initializeMetrics();

            if (configManager.getConfig().getBoolean("update-checker.enabled", true)) {
                new UpdateChecker(this).checkForUpdates();
            }

            displayStartupComplete();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to enable InvRewind!", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        try {
            if (scheduledBackupManager != null) {
                scheduledBackupManager.stop();
            }

            if (databaseManager != null) {
                databaseManager.shutdown();
            }

            if (backupManager != null) {
                backupManager.clearCache();
            }

            getLogger().info("InvRewind has been disabled successfully!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error during plugin shutdown!", e);
        }
    }

    private void registerCommands() {
        InvRewindCommand invRewindCommand = new InvRewindCommand(this, guiManager, messageManager);
        getCommand("invrewind").setExecutor(invRewindCommand);
        getCommand("invrewind").setTabCompleter(invRewindCommand);

        InvRewindForceCommand forceCommand = new InvRewindForceCommand(this, backupManager, messageManager);
        getCommand("invrewindforce").setExecutor(forceCommand);
        getCommand("invrewindforce").setTabCompleter(forceCommand);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new BackupListener(this, backupManager, configManager, scheduledBackupManager), this);
        getServer().getPluginManager().registerEvents(new GUIListener(guiManager), this);
    }

    private void initializeMetrics() {
        try {
            int pluginId = 30747;
            Metrics metrics = new Metrics(this, pluginId);

            metrics.addCustomChart(new SimplePie("database_type", () -> {
                String dbType = configManager.getConfig().getString("database.type", "yaml");
                return dbType.toUpperCase();
            }));

            metrics.addCustomChart(new SimplePie("scheduled_backups", () -> {
                boolean enabled = configManager.getConfig().getBoolean("auto-backup.scheduled.enabled", false);
                return enabled ? "Enabled" : "Disabled";
            }));

            metrics.addCustomChart(new SimplePie("auto_backup_triggers", () -> {
                int count = 0;
                if (configManager.getConfig().getBoolean("auto-backup.on-death", true)) count++;
                if (configManager.getConfig().getBoolean("auto-backup.on-join", true)) count++;
                if (configManager.getConfig().getBoolean("auto-backup.on-quit", true)) count++;
                if (configManager.getConfig().getBoolean("auto-backup.on-world-change", true)) count++;
                return count + " triggers enabled";
            }));
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to initialize bStats metrics", e);
        }
    }

    @NotNull
    public ConfigManager getConfigManager() {
        return configManager;
    }

    @NotNull
    public MessageManager getMessageManager() {
        return messageManager;
    }

    @NotNull
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    @NotNull
    public BackupManager getBackupManager() {
        return backupManager;
    }

    @NotNull
    public GUIManager getGuiManager() {
        return guiManager;
    }

    @NotNull
    public ScheduledBackupManager getScheduledBackupManager() {
        return scheduledBackupManager;
    }

    private void displayStartupLogo() {
        String serverVersion = Bukkit.getVersion();
        String pluginVersion = getDescription().getVersion();
        String mcVersion = Bukkit.getBukkitVersion();
        String serverType = "Unknown";

        if (SchedulerUtil.isFolia()) {
            serverType = "Folia";
        } else {
            String serverName = Bukkit.getName();
            if (serverName.toLowerCase().contains("purpur")) {
                serverType = "Purpur";
            } else if (serverName.toLowerCase().contains("paper")) {
                serverType = "Paper";
            }

            if (serverType.equals("Unknown")) {
                String versionLower = serverVersion.toLowerCase();
                if (versionLower.contains("purpur")) {
                    serverType = "Purpur";
                } else if (versionLower.contains("paper")) {
                    serverType = "Paper";
                }
            }

            if (serverType.equals("Unknown")) {
                try {
                    Class.forName("org.purpurmc.purpur.PurpurConfig");
                    serverType = "Purpur";
                } catch (ClassNotFoundException e1) {
                    try {
                        Class.forName("com.destroystokyo.paper.PaperConfig");
                        serverType = "Paper";
                    } catch (ClassNotFoundException e2) {
                        serverType = "Paper";
                    }
                }
            }
        }

        String orange1 = "\u001B[38;5;202m";
        String orange2 = "\u001B[38;5;208m";
        String orange3 = "\u001B[38;5;214m";
        String orange4 = "\u001B[38;5;220m";
        String orange5 = "\u001B[38;5;226m";
        String gray = "\u001B[38;5;245m";
        String green = "\u001B[38;5;120m";
        String cyan = "\u001B[38;5;51m";
        String yellow = "\u001B[38;5;226m";
        String reset = "\u001B[0m";

        getLogger().info("");
        getLogger().info(orange1 + "  ██╗███╗   ██╗██╗   ██╗██████╗ ███████╗██╗    ██╗██╗███╗   ██╗██████╗ " + reset);
        getLogger().info(orange2 + "  ██║████╗  ██║██║   ██║██╔══██╗██╔════╝██║    ██║██║████╗  ██║██╔══██╗" + reset);
        getLogger().info(orange2 + "  ██║██╔██╗ ██║██║   ██║██████╔╝█████╗  ██║ █╗ ██║██║██╔██╗ ██║██║  ██║" + reset);
        getLogger().info(orange3 + "  ██║██║╚██╗██║╚██╗ ██╔╝██╔══██╗██╔══╝  ██║███╗██║██║██║╚██╗██║██║  ██║" + reset);
        getLogger().info(orange4 + "  ██║██║ ╚████║ ╚████╔╝ ██║  ██║███████╗╚███╔███╔╝██║██║ ╚████║██████╔╝" + reset);
        getLogger().info(orange5 + "  ╚═╝╚═╝  ╚═══╝  ╚═══╝  ╚═╝  ╚═╝╚══════╝ ╚══╝╚══╝ ╚═╝╚═╝  ╚═══╝╚═════╝ " + reset);
        getLogger().info("");
        getLogger().info(orange3 + "                          by mukulx" + reset);
        getLogger().info("");
        getLogger().info(gray + "  Version: " + green + pluginVersion + gray + " | Server: " + cyan + serverType + gray + " | MC: " + yellow + mcVersion + reset);
        getLogger().info("");
    }

    private void displayStartupComplete() {
        String green = "\u001B[38;5;120m";
        String orange = "\u001B[38;5;214m";
        String reset = "\u001B[0m";

        getLogger().info(green + "  ✓ InvRewind loaded successfully!" + reset);
        getLogger().info(orange + "  Commands: /invrewind | /invrewindforce" + reset);
        getLogger().info("");
    }
}
