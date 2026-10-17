package me.hesamai.advancedduty.lang.gui;

import me.hesamai.advancedduty.Main;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

public class LanguageListener implements Listener {

    private final Main plugin;

    public LanguageListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        InventoryHolder h = e.getView().getTopInventory().getHolder();
        if (!(h instanceof LanguageHolder) || !(e.getWhoClicked() instanceof Player)) return;
        e.setCancelled(true);

        Player player = (Player) e.getWhoClicked();
        LanguageHolder holder = (LanguageHolder) h;
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= e.getView().getTopInventory().getSize()) return;

        if (!player.hasPermission("advancedduty.languages")) {
            player.closeInventory();
            plugin.getLanguageManager().send(player, "no-permission");
            return;
        }

        if (holder.getType() == LanguageHolder.Type.MAIN) {
            int page = holder.getPage();
            if (slot == LanguageGUI.PREVIOUS_SLOT) {
                if (page > 1) LanguageGUI.open(plugin, player, page - 1);
            } else if (slot == LanguageGUI.NEXT_SLOT) {
                LanguageGUI.open(plugin, player, page + 1); // clamps to the last page
            } else {
                String code = holder.getSlots().get(slot);
                if (code == null) return;
                if (plugin.getLanguageManager().getCurrentLanguage().equalsIgnoreCase(code)) {
                    plugin.getLanguageManager().send(player, "language-already-active");
                    return;
                }
                LanguageGUI.openConfirm(plugin, player, code, page);
            }
            return;
        }

        if (slot == 11) {
            LanguageGUI.open(plugin, player, holder.getPage());
        } else if (slot == 15 && holder.getSelected() != null) {
            plugin.getLanguageManager().setLanguage(holder.getSelected());
            player.closeInventory();
            plugin.getLanguageManager().send(player, "language-changed",
                    "{language}", plugin.getLanguageManager().getLanguageName(holder.getSelected()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof LanguageHolder) e.setCancelled(true);
    }
}
