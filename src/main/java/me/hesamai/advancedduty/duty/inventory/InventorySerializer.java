package me.hesamai.advancedduty.duty.inventory;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

public final class InventorySerializer {

    private InventorySerializer() {
    }

    public static String itemStackArrayToBase64(ItemStack[] items) {
        if (items == null) {
            items = new ItemStack[0];
        }

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
                dataOutput.writeInt(items.length);
                for (ItemStack item : items) {
                    dataOutput.writeObject(item);
                }
            }
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize ItemStack[]", ex);
        }
    }

    public static ItemStack[] itemStackArrayFromBase64(String data) {
        if (data == null || data.isEmpty()) {
            return new ItemStack[0];
        }

        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
            try (BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
                int size = dataInput.readInt();
                ItemStack[] items = new ItemStack[size];

                for (int i = 0; i < size; i++) {
                    items[i] = (ItemStack) dataInput.readObject();
                }

                return items;
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to deserialize ItemStack[]", ex);
        }
    }

    public static String itemStackToBase64(ItemStack item) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
                dataOutput.writeObject(item);
            }
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize ItemStack", ex);
        }
    }

    public static ItemStack itemStackFromBase64(String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }

        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
            try (BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
                return (ItemStack) dataInput.readObject();
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to deserialize ItemStack", ex);
        }
    }
}