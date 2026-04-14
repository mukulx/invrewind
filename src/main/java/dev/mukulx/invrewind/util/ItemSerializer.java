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

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public class ItemSerializer {

    @Nullable
    public static String serializeItems(@Nullable ItemStack[] items) {
        if (items == null || items.length == 0) {
            return null;
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {

            dataOutput.writeInt(items.length);

            for (ItemStack item : items) {
                dataOutput.writeObject(item);
            }

            return Base64.getEncoder().encodeToString(outputStream.toByteArray());

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Nullable
    public static String serializeItem(@Nullable ItemStack item) {
        if (item == null) {
            return null;
        }
        return serializeItems(new ItemStack[]{item});
    }

    @Nullable
    public static ItemStack[] deserializeItems(@Nullable String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {

            int length = dataInput.readInt();
            ItemStack[] items = new ItemStack[length];

            for (int i = 0; i < length; i++) {
                items[i] = (ItemStack) dataInput.readObject();
            }

            return items;

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Nullable
    public static ItemStack deserializeItem(@Nullable String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }

        ItemStack[] items = deserializeItems(data);
        if (items != null && items.length > 0) {
            return items[0];
        }
        return null;
    }
}
