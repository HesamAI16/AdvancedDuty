package me.hesamai.advancedduty.duty.inventory;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class InventoryUtil {

    private InventoryUtil() {
    }

    public static PlayerInventoryState capture(Player player, boolean includeEnderChest) {
        return new PlayerInventoryState(
                player.getInventory().getContents(),
                player.getInventory().getArmorContents(),
                player.getInventory().getItemInOffHand(),
                includeEnderChest ? player.getEnderChest().getContents() : new ItemStack[0]
        );
    }

    public static void restore(Player player, PlayerInventoryState state, boolean includeEnderChest) {
        if (state == null) {
            clear(player, includeEnderChest);
            return;
        }

        player.getInventory().setContents(state.getContents());
        player.getInventory().setArmorContents(state.getArmorContents());
        player.getInventory().setItemInOffHand(state.getOffHand());

        if (includeEnderChest) {
            player.getEnderChest().setContents(state.getEnderChestContents());
        }

        player.updateInventory();
    }

    public static void clear(Player player, boolean includeEnderChest) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);
        if (includeEnderChest) player.getEnderChest().clear();
        player.updateInventory();
    }

    public static void restore(Player player, PlayerInventoryState state) {
        restore(player, state, true);
    }

    public static void clear(Player player) {
        clear(player, true);
    }

    public static PlayerInventoryState capture(Player player) {
        return capture(player, true);
    }
}