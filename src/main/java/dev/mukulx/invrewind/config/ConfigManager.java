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
package dev.mukulx.invrewind.config;

import dev.mukulx.invrewind.InvRewind;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

public class ConfigManager {

    private final InvRewind plugin;
    private FileConfiguration config;
    private FileConfiguration messages;

    public ConfigManager(@NotNull InvRewind plugin) {
        this.plugin = plugin;
    }

    public void loadConfigs() {

        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        plugin.saveDefaultConfig();
        saveResource("messages.yml");

        config = plugin.getConfig();
        messages = loadConfig("messages.yml");

        checkConfigVersion();
        checkMessagesVersion();

        plugin.getLogger().info("Configuration files loaded successfully");
    }

    private void saveResource(@NotNull String resourcePath) {
        File file = new File(plugin.getDataFolder(), resourcePath);
        if (!file.exists()) {
            plugin.saveResource(resourcePath, false);
        }
    }

    private FileConfiguration loadConfig(@NotNull String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            saveResource(fileName);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private void checkConfigVersion() {
        int currentVersion = config.getInt("plugin.config-version", 0);
        int requiredVersion = 3;

        if (currentVersion < requiredVersion) {
            plugin.getLogger().warning("Config version outdated! Backing up and creating new config...");
            plugin.getLogger().warning("Current version: " + currentVersion + ", Required version: " + requiredVersion);
            migrateConfig(currentVersion, requiredVersion);
        }
    }

    private void checkMessagesVersion() {
        int currentVersion = messages.getInt("messages-version", 0);
        int requiredVersion = 1;

        if (currentVersion < requiredVersion) {
            plugin.getLogger().info("Migrating messages from version " + currentVersion + " to " + requiredVersion);
            migrateMessages(currentVersion, requiredVersion);
        }
    }

    private void migrateConfig(int from, int to) {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        File backupFile = new File(plugin.getDataFolder(), "config.yml.backup.v" + from + "." + System.currentTimeMillis());

        try {
            if (configFile.exists()) {
                FileConfiguration oldConfig = YamlConfiguration.loadConfiguration(configFile);
                java.nio.file.Files.copy(configFile.toPath(), backupFile.toPath());
                plugin.getLogger().info("Old config backed up to: " + backupFile.getName());
                
                configFile.delete();
            }

            plugin.saveResource("config.yml", false);
            plugin.reloadConfig();
            config = plugin.getConfig();
            
            if (backupFile.exists()) {
                FileConfiguration oldConfig = YamlConfiguration.loadConfiguration(backupFile);
                migrateConfigValues(oldConfig, config);
                plugin.saveConfig();
            }

            plugin.getLogger().info("Config migrated from v" + from + " to v" + to);
            plugin.getLogger().info("Please review the new config and update your settings from backup if needed");
            plugin.getLogger().info("Backup location: " + backupFile.getName());

        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to migrate config!", e);
        }
    }

    private void migrateConfigValues(@NotNull FileConfiguration oldConfig, @NotNull FileConfiguration newConfig) {

        if (oldConfig.contains("database.type")) {
            newConfig.set("database.type", oldConfig.getString("database.type"));
        }
        if (oldConfig.contains("database.yaml.folder")) {
            newConfig.set("database.yaml.folder", oldConfig.getString("database.yaml.folder"));
        }
        if (oldConfig.contains("database.yaml.pretty-print")) {
            newConfig.set("database.yaml.pretty-print", oldConfig.getBoolean("database.yaml.pretty-print"));
        }
        if (oldConfig.contains("database.sqlite.file")) {
            newConfig.set("database.sqlite.file", oldConfig.getString("database.sqlite.file"));
        }
        if (oldConfig.contains("database.mysql.host")) {
            newConfig.set("database.mysql.host", oldConfig.getString("database.mysql.host"));
        }
        if (oldConfig.contains("database.mysql.port")) {
            newConfig.set("database.mysql.port", oldConfig.getInt("database.mysql.port"));
        }
        if (oldConfig.contains("database.mysql.database")) {
            newConfig.set("database.mysql.database", oldConfig.getString("database.mysql.database"));
        }
        if (oldConfig.contains("database.mysql.username")) {
            newConfig.set("database.mysql.username", oldConfig.getString("database.mysql.username"));
        }
        if (oldConfig.contains("database.mysql.password")) {
            newConfig.set("database.mysql.password", oldConfig.getString("database.mysql.password"));
        }

        if (oldConfig.contains("database.mysql.pool.maximum-pool-size")) {
            newConfig.set("database.mysql.pool.maximum-pool-size", oldConfig.getInt("database.mysql.pool.maximum-pool-size"));
        }
        if (oldConfig.contains("database.mysql.pool.minimum-idle")) {
            newConfig.set("database.mysql.pool.minimum-idle", oldConfig.getInt("database.mysql.pool.minimum-idle"));
        }
        if (oldConfig.contains("database.mysql.pool.connection-timeout")) {
            newConfig.set("database.mysql.pool.connection-timeout", oldConfig.getInt("database.mysql.pool.connection-timeout"));
        }
        if (oldConfig.contains("database.mysql.pool.idle-timeout")) {
            newConfig.set("database.mysql.pool.idle-timeout", oldConfig.getInt("database.mysql.pool.idle-timeout"));
        }
        if (oldConfig.contains("database.mysql.pool.max-lifetime")) {
            newConfig.set("database.mysql.pool.max-lifetime", oldConfig.getInt("database.mysql.pool.max-lifetime"));
        }

        if (oldConfig.contains("auto-backup.on-death")) {
            newConfig.set("auto-backup.on-death", oldConfig.getBoolean("auto-backup.on-death"));
        }
        if (oldConfig.contains("auto-backup.death-event-priority-low")) {
            newConfig.set("auto-backup.death-event-priority-low", oldConfig.getBoolean("auto-backup.death-event-priority-low"));
        }
        if (oldConfig.contains("auto-backup.on-world-change")) {
            newConfig.set("auto-backup.on-world-change", oldConfig.getBoolean("auto-backup.on-world-change"));
        }
        if (oldConfig.contains("auto-backup.on-join")) {
            newConfig.set("auto-backup.on-join", oldConfig.getBoolean("auto-backup.on-join"));
        }
        if (oldConfig.contains("auto-backup.on-quit")) {
            newConfig.set("auto-backup.on-quit", oldConfig.getBoolean("auto-backup.on-quit"));
        }

        if (oldConfig.contains("auto-backup.max-backups-per-type")) {
            int oldMax = oldConfig.getInt("auto-backup.max-backups-per-type");
            newConfig.set("auto-backup.limits.max-per-type.death", oldMax);
            newConfig.set("auto-backup.limits.max-per-type.world-change", oldMax);
            newConfig.set("auto-backup.limits.max-per-type.join", oldMax);
            newConfig.set("auto-backup.limits.max-per-type.quit", oldMax);
            newConfig.set("auto-backup.limits.max-per-type.scheduled", oldMax);
            newConfig.set("auto-backup.limits.max-per-type.force", oldMax);
        }

        if (oldConfig.contains("auto-backup.limits.enabled")) {
            newConfig.set("auto-backup.limits.enabled", oldConfig.getBoolean("auto-backup.limits.enabled"));
        }
        if (oldConfig.contains("auto-backup.limits.max-per-type.death")) {
            newConfig.set("auto-backup.limits.max-per-type.death", oldConfig.getInt("auto-backup.limits.max-per-type.death"));
        }
        if (oldConfig.contains("auto-backup.limits.max-per-type.world-change")) {
            newConfig.set("auto-backup.limits.max-per-type.world-change", oldConfig.getInt("auto-backup.limits.max-per-type.world-change"));
        }
        if (oldConfig.contains("auto-backup.limits.max-per-type.join")) {
            newConfig.set("auto-backup.limits.max-per-type.join", oldConfig.getInt("auto-backup.limits.max-per-type.join"));
        }
        if (oldConfig.contains("auto-backup.limits.max-per-type.quit")) {
            newConfig.set("auto-backup.limits.max-per-type.quit", oldConfig.getInt("auto-backup.limits.max-per-type.quit"));
        }
        if (oldConfig.contains("auto-backup.limits.max-per-type.scheduled")) {
            newConfig.set("auto-backup.limits.max-per-type.scheduled", oldConfig.getInt("auto-backup.limits.max-per-type.scheduled"));
        }

        if (oldConfig.contains("auto-backup.limits.max-per-type.manual")) {
            newConfig.set("auto-backup.limits.max-per-type.force", oldConfig.getInt("auto-backup.limits.max-per-type.manual"));
        }
        if (oldConfig.contains("auto-backup.limits.max-per-type.force")) {
            newConfig.set("auto-backup.limits.max-per-type.force", oldConfig.getInt("auto-backup.limits.max-per-type.force"));
        }

        if (oldConfig.contains("auto-backup.scheduled.enabled")) {
            newConfig.set("auto-backup.scheduled.enabled", oldConfig.getBoolean("auto-backup.scheduled.enabled"));
        }
        if (oldConfig.contains("auto-backup.scheduled.interval")) {
            newConfig.set("auto-backup.scheduled.interval", oldConfig.getInt("auto-backup.scheduled.interval"));
        }
        if (oldConfig.contains("auto-backup.scheduled.require-movement")) {
            newConfig.set("auto-backup.scheduled.require-movement", oldConfig.getBoolean("auto-backup.scheduled.require-movement"));
        }
        if (oldConfig.contains("auto-backup.scheduled.min-items")) {
            newConfig.set("auto-backup.scheduled.min-items", oldConfig.getInt("auto-backup.scheduled.min-items"));
        }
        if (oldConfig.contains("auto-backup.scheduled.notify-player")) {
            newConfig.set("auto-backup.scheduled.notify-player", oldConfig.getBoolean("auto-backup.scheduled.notify-player"));
        }

        if (oldConfig.contains("features.save-inventory")) {
            newConfig.set("features.save-inventory", oldConfig.getBoolean("features.save-inventory"));
        }
        if (oldConfig.contains("features.save-armor")) {
            newConfig.set("features.save-armor", oldConfig.getBoolean("features.save-armor"));
        }
        if (oldConfig.contains("features.save-offhand")) {
            newConfig.set("features.save-offhand", oldConfig.getBoolean("features.save-offhand"));
        }
        if (oldConfig.contains("features.save-enderchest")) {
            newConfig.set("features.save-enderchest", oldConfig.getBoolean("features.save-enderchest"));
        }
        if (oldConfig.contains("features.save-health")) {
            newConfig.set("features.save-health", oldConfig.getBoolean("features.save-health"));
        }
        if (oldConfig.contains("features.save-hunger")) {
            newConfig.set("features.save-hunger", oldConfig.getBoolean("features.save-hunger"));
        }
        if (oldConfig.contains("features.save-xp")) {
            newConfig.set("features.save-xp", oldConfig.getBoolean("features.save-xp"));
        }
        if (oldConfig.contains("features.save-location")) {
            newConfig.set("features.save-location", oldConfig.getBoolean("features.save-location"));
        }

        if (oldConfig.contains("restore.require-online")) {
            newConfig.set("restore.require-online", oldConfig.getBoolean("restore.require-online"));
        }

        if (oldConfig.contains("restore.full-restore.inventory")) {
            newConfig.set("restore.full-restore.inventory", oldConfig.getBoolean("restore.full-restore.inventory"));
        }
        if (oldConfig.contains("restore.full-restore.armor")) {
            newConfig.set("restore.full-restore.armor", oldConfig.getBoolean("restore.full-restore.armor"));
        }
        if (oldConfig.contains("restore.full-restore.offhand")) {
            newConfig.set("restore.full-restore.offhand", oldConfig.getBoolean("restore.full-restore.offhand"));
        }
        if (oldConfig.contains("restore.full-restore.enderchest")) {
            newConfig.set("restore.full-restore.enderchest", oldConfig.getBoolean("restore.full-restore.enderchest"));
        }
        if (oldConfig.contains("restore.full-restore.health")) {
            newConfig.set("restore.full-restore.health", oldConfig.getBoolean("restore.full-restore.health"));
        }
        if (oldConfig.contains("restore.full-restore.hunger")) {
            newConfig.set("restore.full-restore.hunger", oldConfig.getBoolean("restore.full-restore.hunger"));
        }
        if (oldConfig.contains("restore.full-restore.xp")) {
            newConfig.set("restore.full-restore.xp", oldConfig.getBoolean("restore.full-restore.xp"));
        }
        if (oldConfig.contains("restore.full-restore.location")) {
            newConfig.set("restore.full-restore.location", oldConfig.getBoolean("restore.full-restore.location"));
        }

        if (oldConfig.contains("restore.overwrite.inventory")) {
            newConfig.set("restore.overwrite.inventory", oldConfig.getBoolean("restore.overwrite.inventory"));
        }
        if (oldConfig.contains("restore.overwrite.armor")) {
            newConfig.set("restore.overwrite.armor", oldConfig.getBoolean("restore.overwrite.armor"));
        }
        if (oldConfig.contains("restore.overwrite.offhand")) {
            newConfig.set("restore.overwrite.offhand", oldConfig.getBoolean("restore.overwrite.offhand"));
        }
        if (oldConfig.contains("restore.overwrite.enderchest")) {
            newConfig.set("restore.overwrite.enderchest", oldConfig.getBoolean("restore.overwrite.enderchest"));
        }
        if (oldConfig.contains("restore.overwrite.health")) {
            newConfig.set("restore.overwrite.health", oldConfig.getBoolean("restore.overwrite.health"));
        }
        if (oldConfig.contains("restore.overwrite.hunger")) {
            newConfig.set("restore.overwrite.hunger", oldConfig.getBoolean("restore.overwrite.hunger"));
        }
        if (oldConfig.contains("restore.overwrite.xp")) {
            newConfig.set("restore.overwrite.xp", oldConfig.getBoolean("restore.overwrite.xp"));
        }
        if (oldConfig.contains("restore.overwrite.location")) {
            newConfig.set("restore.overwrite.location", oldConfig.getBoolean("restore.overwrite.location"));
        }

        if (oldConfig.contains("restore.buttons.restore-inventory")) {
            newConfig.set("restore.buttons.restore-inventory", oldConfig.getBoolean("restore.buttons.restore-inventory"));
        }
        if (oldConfig.contains("restore.buttons.restore-armor")) {
            newConfig.set("restore.buttons.restore-armor", oldConfig.getBoolean("restore.buttons.restore-armor"));
        }
        if (oldConfig.contains("restore.buttons.restore-offhand")) {
            newConfig.set("restore.buttons.restore-offhand", oldConfig.getBoolean("restore.buttons.restore-offhand"));
        }
        if (oldConfig.contains("restore.buttons.restore-enderchest")) {
            newConfig.set("restore.buttons.restore-enderchest", oldConfig.getBoolean("restore.buttons.restore-enderchest"));
        }
        if (oldConfig.contains("restore.buttons.restore-health")) {
            newConfig.set("restore.buttons.restore-health", oldConfig.getBoolean("restore.buttons.restore-health"));
        }
        if (oldConfig.contains("restore.buttons.restore-hunger")) {
            newConfig.set("restore.buttons.restore-hunger", oldConfig.getBoolean("restore.buttons.restore-hunger"));
        }
        if (oldConfig.contains("restore.buttons.restore-xp")) {
            newConfig.set("restore.buttons.restore-xp", oldConfig.getBoolean("restore.buttons.restore-xp"));
        }
        if (oldConfig.contains("restore.buttons.teleport-location")) {
            newConfig.set("restore.buttons.teleport-location", oldConfig.getBoolean("restore.buttons.teleport-location"));
        }

        if (oldConfig.contains("time.zone")) {
            newConfig.set("time.zone", oldConfig.getString("time.zone"));
        }
        if (oldConfig.contains("time.format")) {
            newConfig.set("time.format", oldConfig.getString("time.format"));
        }

        if (oldConfig.contains("gui.sounds.enabled")) {
            newConfig.set("gui.sounds.enabled", oldConfig.getBoolean("gui.sounds.enabled"));
        }
        if (oldConfig.contains("gui.sounds.open")) {
            newConfig.set("gui.sounds.open", oldConfig.getString("gui.sounds.open"));
        }
        if (oldConfig.contains("gui.sounds.click")) {
            newConfig.set("gui.sounds.click", oldConfig.getString("gui.sounds.click"));
        }
        if (oldConfig.contains("gui.sounds.restore-success")) {
            newConfig.set("gui.sounds.restore-success", oldConfig.getString("gui.sounds.restore-success"));
        }
        if (oldConfig.contains("gui.sounds.restore-fail")) {
            newConfig.set("gui.sounds.restore-fail", oldConfig.getString("gui.sounds.restore-fail"));
        }
        if (oldConfig.contains("gui.sounds.page-turn")) {
            newConfig.set("gui.sounds.page-turn", oldConfig.getString("gui.sounds.page-turn"));
        }
        if (oldConfig.contains("gui.sounds.close")) {
            newConfig.set("gui.sounds.close", oldConfig.getString("gui.sounds.close"));
        }
        if (oldConfig.contains("gui.sounds.volume")) {
            newConfig.set("gui.sounds.volume", oldConfig.getDouble("gui.sounds.volume"));
        }
        if (oldConfig.contains("gui.sounds.pitch")) {
            newConfig.set("gui.sounds.pitch", oldConfig.getDouble("gui.sounds.pitch"));
        }

        if (oldConfig.contains("debug.enabled")) {
            newConfig.set("debug.enabled", oldConfig.getBoolean("debug.enabled"));
        }

        if (oldConfig.contains("update-checker.enabled")) {
            newConfig.set("update-checker.enabled", oldConfig.getBoolean("update-checker.enabled"));
        }

        if (oldConfig.contains("migration.delete-source-after-import")) {
            newConfig.set("migration.delete-source-after-import", oldConfig.getBoolean("migration.delete-source-after-import"));
        }

        if (oldConfig.contains("offline-restore.enabled")) {
            newConfig.set("offline-restore.enabled", oldConfig.getBoolean("offline-restore.enabled"));
        }
        if (oldConfig.contains("offline-restore.expiry-hours")) {
            newConfig.set("offline-restore.expiry-hours", oldConfig.getLong("offline-restore.expiry-hours"));
        }
        if (oldConfig.contains("offline-restore.expiry-check-interval")) {
            newConfig.set("offline-restore.expiry-check-interval", oldConfig.getLong("offline-restore.expiry-check-interval"));
        }
        if (oldConfig.contains("offline-restore.apply-delay-ticks")) {
            newConfig.set("offline-restore.apply-delay-ticks", oldConfig.getLong("offline-restore.apply-delay-ticks"));
        }
        if (oldConfig.contains("offline-restore.freeze-player")) {
            newConfig.set("offline-restore.freeze-player", oldConfig.getBoolean("offline-restore.freeze-player"));
        }
        if (oldConfig.contains("offline-restore.title")) {
            newConfig.set("offline-restore.title", oldConfig.getString("offline-restore.title"));
        }
        if (oldConfig.contains("offline-restore.subtitle")) {
            newConfig.set("offline-restore.subtitle", oldConfig.getString("offline-restore.subtitle"));
        }
        if (oldConfig.contains("offline-restore.title-fade-in")) {
            newConfig.set("offline-restore.title-fade-in", oldConfig.getInt("offline-restore.title-fade-in"));
        }
        if (oldConfig.contains("offline-restore.title-stay")) {
            newConfig.set("offline-restore.title-stay", oldConfig.getInt("offline-restore.title-stay"));
        }
        if (oldConfig.contains("offline-restore.title-fade-out")) {
            newConfig.set("offline-restore.title-fade-out", oldConfig.getInt("offline-restore.title-fade-out"));
        }

        plugin.getLogger().info("Migrated " + countMigratedValues(oldConfig, newConfig) + " settings from old config");
    }

    private int countMigratedValues(@NotNull FileConfiguration oldConfig, @NotNull FileConfiguration newConfig) {
        int count = 0;
        for (String key : oldConfig.getKeys(true)) {
            if (!oldConfig.isConfigurationSection(key) && newConfig.contains(key)) {
                count++;
            }
        }
        return count;
    }

    private void migrateMessages(int from, int to) {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        File backupFile = new File(plugin.getDataFolder(), "messages.yml.backup.v" + from + "." + System.currentTimeMillis());

        try {
            if (messagesFile.exists()) {
                java.nio.file.Files.copy(messagesFile.toPath(), backupFile.toPath());
                plugin.getLogger().info("Old messages backed up to: " + backupFile.getName());
            }

            plugin.saveResource("messages.yml", true);
            messages = loadConfig("messages.yml");

            plugin.getLogger().info("Messages migrated from v" + from + " to v" + to);

        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to migrate messages!", e);
        }
    }

    public void reload() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        messages = loadConfig("messages.yml");
        plugin.getLogger().info("Configuration reloaded successfully");
    }

    @NotNull
    public FileConfiguration getConfig() {
        return config;
    }

    @NotNull
    public FileConfiguration getMessages() {
        return messages;
    }
}
