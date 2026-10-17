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

    /** Like {@link #capture(Player, boolean)} but leaves out items matching {@code skip} (staff tools). */
    public static PlayerInventoryState captureWithout(Player player, boolean includeEnderChest,
                                                      java.util.function.Predicate<ItemStack> skip) {
        ItemStack[] contents = filter(player.getInventory().getContents(), skip);
        ItemStack[] armor = filter(player.getInventory().getArmorContents(), skip);
        ItemStack off = player.getInventory().getItemInOffHand();
        if (off != null && skip.test(off)) off = null;
        ItemStack[] ender = includeEnderChest ? filter(player.getEnderChest().getContents(), skip) : new ItemStack[0];
        return new PlayerInventoryState(contents, armor, off, ender);
    }

    private static ItemStack[] filter(ItemStack[] source, java.util.function.Predicate<ItemStack> skip) {
        ItemStack[] out = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            ItemStack it = source[i];
            out[i] = (it == null || skip.test(it)) ? null : it.clone();
        }
        return out;
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