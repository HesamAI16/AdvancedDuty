package me.hesamai.advancedduty.lang.gui;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

/** Remembers which language sits in which slot, so clicks never have to parse item lore. */
public class LanguageHolder implements InventoryHolder {

    public enum Type { MAIN, CONFIRM }

    private final Type type;
    private final int page;
    private final Map<Integer, String> slots = new HashMap<>();
    private String selected;
    private Inventory inventory;

    public LanguageHolder(Type type, int page) {
        this.type = type;
        this.page = page;
    }

    public Inventory create(int size, String title) {
        inventory = Bukkit.createInventory(this, size, title);
        return inventory;
    }

    public Type getType() { return type; }
    public int getPage() { return page; }
    public Map<Integer, String> getSlots() { return slots; }
    public String getSelected() { return selected; }
    public void setSelected(String selected) { this.selected = selected; }

    @Override
    public Inventory getInventory() { return inventory; }
}
