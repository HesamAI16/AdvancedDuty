package me.hesamai.advancedduty.lang.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class LanguageHolder implements InventoryHolder {

    public enum Type { MAIN, CONFIRM }

    private final Type type;
    private final int page;

    public LanguageHolder(Type type, int page){
        this.type = type;
        this.page = page;
    }

    public Type getType(){
        return type;
    }

    public int getPage(){
        return page;
    }

    @Override
    public Inventory getInventory(){
        return null;
    }
}