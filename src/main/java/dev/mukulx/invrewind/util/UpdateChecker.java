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

import dev.mukulx.invrewind.InvRewind;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Level;

public class UpdateChecker {

    private static final String GITHUB_API = "https://api.github.com/repos/mukulx/invrewind/releases/latest";
    private final InvRewind plugin;
    private String latestVersion;
    private String downloadUrl;

    public UpdateChecker(@NotNull InvRewind plugin) {
        this.plugin = plugin;
    }

    public void checkForUpdates() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URL url = new URL(GITHUB_API);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;

                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    parseResponse(response.toString());
                } else {
                    plugin.getLogger().warning("Failed to check for updates. Response code: " + responseCode);
                }

                connection.disconnect();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to check for updates", e);
            }
        });
    }

    private void parseResponse(@NotNull String json) {
        try {
            int tagNameIndex = json.indexOf("\"tag_name\":");
            if (tagNameIndex != -1) {
                int start = json.indexOf("\"", tagNameIndex + 11) + 1;
                int end = json.indexOf("\"", start);
                latestVersion = json.substring(start, end).replace("v", "");
            }

            int htmlUrlIndex = json.indexOf("\"html_url\":");
            if (htmlUrlIndex != -1) {
                int start = json.indexOf("\"", htmlUrlIndex + 11) + 1;
                int end = json.indexOf("\"", start);
                downloadUrl = json.substring(start, end);
            }

            if (latestVersion != null) {
                String currentVersion = plugin.getDescription().getVersion();
                if (isNewerVersion(currentVersion, latestVersion)) {
                    notifyUpdate();
                } else {
                    plugin.getLogger().info("You are running the latest version.");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to parse update response", e);
        }
    }

    private boolean isNewerVersion(@NotNull String current, @NotNull String latest) {
        String[] currentParts = current.split("\\.");
        String[] latestParts = latest.split("\\.");

        int length = Math.max(currentParts.length, latestParts.length);
        for (int i = 0; i < length; i++) {
            int currentPart = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;
            int latestPart = i < latestParts.length ? Integer.parseInt(latestParts[i]) : 0;

            if (latestPart > currentPart) {
                return true;
            } else if (latestPart < currentPart) {
                return false;
            }
        }
        return false;
    }

    private void notifyUpdate() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            plugin.getLogger().info("========================================");
            plugin.getLogger().info("A new version is available!");
            plugin.getLogger().info("Current: " + plugin.getDescription().getVersion());
            plugin.getLogger().info("Latest: " + latestVersion);
            plugin.getLogger().info("Download: " + downloadUrl);
            plugin.getLogger().info("========================================");
        });
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }
}
