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
package dev.mukulx.invrewind.managers;

import dev.mukulx.invrewind.InvRewind;
import dev.mukulx.invrewind.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class MessageManager {

    private final InvRewind plugin;
    private final ConfigManager configManager;
    private final MiniMessage miniMessage;

    public MessageManager(@NotNull InvRewind plugin, @NotNull ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.miniMessage = MiniMessage.miniMessage();
    }

    @NotNull
    public Component toSmallCaps(@NotNull String text) {
        String smallCaps = text.toLowerCase()
            .replace("a", "ᴀ").replace("b", "ʙ").replace("c", "ᴄ").replace("d", "ᴅ")
            .replace("e", "ᴇ").replace("f", "ғ").replace("g", "ɢ").replace("h", "ʜ")
            .replace("i", "ɪ").replace("j", "ᴊ").replace("k", "ᴋ").replace("l", "ʟ")
            .replace("m", "ᴍ").replace("n", "ɴ").replace("o", "ᴏ").replace("p", "ᴘ")
            .replace("q", "ǫ").replace("r", "ʀ").replace("s", "s").replace("t", "ᴛ")
            .replace("u", "ᴜ").replace("v", "ᴠ").replace("w", "ᴡ").replace("x", "x")
            .replace("y", "ʏ").replace("z", "ᴢ");
        return Component.text(smallCaps);
    }

    public void sendMessage(@NotNull CommandSender sender, @NotNull String path) {
        sendMessage(sender, path, null);
    }

    public void sendMessage(@NotNull CommandSender sender, @NotNull String path, @Nullable Map<String, String> placeholders) {
        String message = getMessageString(path, placeholders);
        if (message != null && !message.isEmpty()) {
            Component component = miniMessage.deserialize(message);
            sender.sendMessage(component);
        }
    }

    @Nullable
    public String getMessageString(@NotNull String path) {
        return getMessageString(path, null);
    }

    @Nullable
    public String getMessageString(@NotNull String path, @Nullable Map<String, String> placeholders) {
        FileConfiguration messages = configManager.getMessages();
        String message = messages.getString(path);

        if (message == null) {
            plugin.getLogger().warning("Missing message: " + path);
            return null;
        }

        boolean usePrefix = messages.getBoolean("settings.use-prefix", true);
        if (usePrefix && !path.startsWith("settings.")) {
            String prefix = messages.getString("settings.prefix", "");
            if (prefix != null && !prefix.isEmpty() && !message.contains("{prefix}")) {
                message = prefix + message;
            }
        }

        String prefix = messages.getString("settings.prefix", "");
        if (prefix != null) {
            message = message.replace("{prefix}", prefix);
        }

        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }

        return message;
    }

    @NotNull
    public Component getComponent(@NotNull String path) {
        return getComponent(path, null);
    }

    @NotNull
    public Component getComponent(@NotNull String path, @Nullable Map<String, String> placeholders) {
        String message = getMessageString(path, placeholders);
        if (message == null) {
            return Component.empty();
        }
        return miniMessage.deserialize(message);
    }

    @NotNull
    public Component getMessage(@NotNull String path) {
        return getComponent(path, null);
    }

    @NotNull
    public Component getMessage(@NotNull String path, @Nullable Map<String, String> placeholders) {
        return getComponent(path, placeholders);
    }

    @NotNull
    public String getRawMessage(@NotNull String path) {
        String message = getMessageString(path, null);
        return message != null ? message : "";
    }

    @NotNull
    public Component parseMessage(@NotNull String message) {
        return miniMessage.deserialize(message);
    }
}
