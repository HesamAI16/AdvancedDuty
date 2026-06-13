package me.hesamai.advancedduty.duty.inventory;

import org.bukkit.inventory.ItemStack;

public final class PlayerInventoryState {

    private final ItemStack[] contents;
    private final ItemStack[] armorContents;
    private final ItemStack offHand;
    private final ItemStack[] enderChestContents;

    public PlayerInventoryState(ItemStack[] contents,
                                ItemStack[] armorContents,
                                ItemStack offHand,
                                ItemStack[] enderChestContents) {
        this.contents = cloneArray(contents);
        this.armorContents = cloneArray(armorContents);
        this.offHand = cloneItem(offHand);
        this.enderChestContents = cloneArray(enderChestContents);
    }

    public ItemStack[] getContents() {
        return cloneArray(contents);
    }

    public ItemStack[] getArmorContents() {
        return cloneArray(armorContents);
    }

    public ItemStack getOffHand() {
        return cloneItem(offHand);
    }

    public ItemStack[] getEnderChestContents() {
        return cloneArray(enderChestContents);
    }

    public PlayerInventoryState copy() {
        return new PlayerInventoryState(contents, armorContents, offHand, enderChestContents);
    }

    private static ItemStack[] cloneArray(ItemStack[] source) {
        if (source == null) {
            return new ItemStack[0];
        }

        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = cloneItem(source[i]);
        }
        return copy;
    }

    private static ItemStack cloneItem(ItemStack item) {
        return item == null ? null : item.clone();
    }
}