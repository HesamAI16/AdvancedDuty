package me.hesamai.advancedduty.feature;

import me.hesamai.advancedduty.Main;
import me.hesamai.advancedduty.gui.GuiHolder;
import me.hesamai.advancedduty.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Looks into another player's inventory / ender chest. Without {@code advancedduty.inspect.edit} the
 * view is a read-only copy; with it the staff member gets the real, editable inventory.
 */
public class InspectManager implements Listener {

    private final Main plugin;

    public InspectManager(Main plugin) {
        this.plugin = plugin;
    }

    public void open(Player staff, Player target) {
        if (staff.equals(target)) {
            plugin.getLanguageManager().send(staff, "inspect-self");
            return;
        }
        if (target.hasPermission("advancedduty.inspect.exempt") && !staff.hasPermission("advancedduty.inspect.exempt.override")) {
            plugin.getLanguageManager().send(staff, "inspect-exempt", "{player}", target.getName());
            return;
        }
        if (staff.hasPermission("advancedduty.inspect.edit")) {
            staff.openInventory(target.getInventory());
        } else {
            openSnapshot(staff, target);
        }
        plugin.getLanguageManager().send(staff, "inspect-opened", "{player}", target.getName());
    }

    public void openEnderChest(Player staff, Player target) {
        if (target.hasPermission("advancedduty.inspect.exempt") && !staff.hasPermission("advancedduty.inspect.exempt.override")) {
            plugin.getLanguageManager().send(staff, "inspect-exempt", "{player}", target.getName());
            return;
        }
        if (staff.hasPermission("advancedduty.inspect.edit")) {
            staff.openInventory(target.getEnderChest());
            return;
        }
        GuiHolder holder = new GuiHolder(GuiHolder.Type.INSPECT, 0, target.getUniqueId());
        Inventory inv = holder.create(27, plugin.getLanguageManager().gui("inspect.ender-title", "{player}", target.getName()));
        ItemStack[] contents = target.getEnderChest().getContents();
        for (int i = 0; i < Math.min(27, contents.length); i++) inv.setItem(i, contents[i] == null ? null : contents[i].clone());
        staff.openInventory(inv);
    }

    private void openSnapshot(Player staff, Player target) {
        GuiHolder holder = new GuiHolder(GuiHolder.Type.INSPECT, 0, target.getUniqueId());
        Inventory inv = holder.create(54, plugin.getLanguageManager().gui("inspect.title", "{player}", target.getName()));
        PlayerInventory pi = target.getInventory();

        for (int i = 9; i < 36; i++) inv.setItem(i - 9, copy(pi.getItem(i)));      // main inventory rows 0-2
        for (int i = 0; i < 9; i++) inv.setItem(27 + i, copy(pi.getItem(i)));        // hotbar row 3

        ItemStack[] armor = pi.getArmorContents(); // boots, leggings, chest, helmet
        inv.setItem(36, copy(armor[3]));
        inv.setItem(37, copy(armor[2]));
        inv.setItem(38, copy(armor[1]));
        inv.setItem(39, copy(armor[0]));
        inv.setItem(40, copy(pi.getItemInOffHand()));

        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 41; i < 45; i++) inv.setItem(i, filler);
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        List<String> lore = new ArrayList<>();
        lore.add(plugin.getLanguageManager().gui("inspect.health", "{value}", String.format("%.1f", target.getHealth())));
        lore.add(plugin.getLanguageManager().gui("inspect.food", "{value}", String.valueOf(target.getFoodLevel())));
        lore.add(plugin.getLanguageManager().gui("inspect.level", "{value}", String.valueOf(target.getLevel())));
        lore.add(plugin.getLanguageManager().gui("inspect.gamemode", "{value}", target.getGameMode().name()));
        lore.add(plugin.getLanguageManager().gui("inspect.world", "{value}", target.getWorld().getName()));
        lore.add(plugin.getLanguageManager().gui("inspect.ping", "{value}", String.valueOf(target.getPing())));
        inv.setItem(49, new ItemBuilder(Material.PLAYER_HEAD).name("&e" + target.getName()).lore(lore).skullOwner(target).build());
        inv.setItem(51, new ItemBuilder(Material.ENDER_CHEST).name(plugin.getLanguageManager().gui("inspect.ender-button")).build());
        inv.setItem(47, new ItemBuilder(Material.CLOCK).name(plugin.getLanguageManager().gui("inspect.refresh")).build());
        staff.openInventory(inv);
    }

    private static ItemStack copy(ItemStack s) {
        return s == null ? null : s.clone();
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof GuiHolder)) return;
        GuiHolder holder = (GuiHolder) e.getInventory().getHolder();
        if (holder.getType() != GuiHolder.Type.INSPECT) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player) || e.getClickedInventory() != e.getInventory()) return;
        Player staff = (Player) e.getWhoClicked();
        Player target = holder.getTarget() == null ? null : plugin.getServer().getPlayer(holder.getTarget());
        if (target == null) {
            staff.closeInventory();
            return;
        }
        if (e.getSlot() == 51 && e.getInventory().getSize() == 54) openEnderChest(staff, target);
        else if (e.getSlot() == 47 && e.getInventory().getSize() == 54) openSnapshot(staff, target);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof GuiHolder) e.setCancelled(true);
    }
}
