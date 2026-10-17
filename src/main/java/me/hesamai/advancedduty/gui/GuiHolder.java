package me.hesamai.advancedduty.gui;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/** Marks inventories created by this plugin so clicks can be recognised (and cancelled). */
public class GuiHolder implements InventoryHolder {

    public enum Type { MENU, STAFF, PLAYERS, INSPECT, HISTORY, TOP }

    private final Type type;
    private final int page;
    private final UUID target;
    private Inventory inventory;

    public GuiHolder(Type type, int page, UUID target) {
        this.type = type;
        this.page = page;
        this.target = target;
    }

    public Inventory create(int size, String title) {
        inventory = Bukkit.createInventory(this, size, title);
        return inventory;
    }

    public Type getType() { return type; }
    public int getPage() { return page; }
    public UUID getTarget() { return target; }

    @Override
    public Inventory getInventory() { return inventory; }
}
