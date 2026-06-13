package me.hesamai.advancedduty.lang.gui;

import me.hesamai.advancedduty.Main;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.*;

public class LanguageListener implements Listener {

    private final Main plugin;

    private final Set<UUID> mainGuiOpen    = new HashSet<>();
    private final Set<UUID> confirmGuiOpen = new HashSet<>();

    private static final int[] LANGUAGE_SLOTS = {
            10,11,12,13,14,15,16,
            19,20,21,22,23,24,25,
            28,29,30,31,32,33,34
    };

    private static final int PREVIOUS_SLOT = 47;
    private static final int NEXT_SLOT     = 51;

    public LanguageListener(Main plugin) {
        this.plugin = plugin;
    }

    public void markMainGui(UUID uuid)    { mainGuiOpen.add(uuid);    confirmGuiOpen.remove(uuid); }
    public void markConfirmGui(UUID uuid) { confirmGuiOpen.add(uuid); mainGuiOpen.remove(uuid);    }
    public void clearGui(UUID uuid)       { mainGuiOpen.remove(uuid); confirmGuiOpen.remove(uuid); }

    @EventHandler
    public void onClick(InventoryClickEvent e){

        if(!(e.getWhoClicked() instanceof Player)) return;

        if(!(e.getView().getTopInventory().getHolder() instanceof LanguageHolder)) return;

        e.setCancelled(true);

        Player player = (Player)e.getWhoClicked();
        LanguageHolder holder = (LanguageHolder)e.getView().getTopInventory().getHolder();

        int slot = e.getRawSlot();
        if(slot < 0 || slot >= e.getView().getTopInventory().getSize()) return;

        if(holder.getType() == LanguageHolder.Type.MAIN){

            int currentPage = holder.getPage();

            if(slot == 47){
                if(currentPage > 1)
                    LanguageGUI.open(plugin,player,currentPage-1);
                return;
            }

            if(slot == 51){
                LanguageGUI.open(plugin,player,currentPage+1);
                return;
            }

            ItemStack clicked = e.getCurrentItem();
            if(clicked == null || clicked.getType().isAir()) return;

            String lang = extractLangFromItem(clicked);
            if(lang == null) return;

            if(plugin.getLanguageManager().getCurrentLanguage().equalsIgnoreCase(lang)){
                player.sendMessage(
                        plugin.getLanguageManager().getMessage("language-already-active")
                );
                return;
            }

            LanguageGUI.openConfirm(plugin,player,lang,currentPage);
            return;
        }

        if(holder.getType() == LanguageHolder.Type.CONFIRM){

            int returnPage = holder.getPage();

            if(slot == 11){
                LanguageGUI.open(plugin,player,returnPage);
                return;
            }

            if(slot == 15){

                String lang = extractLangFromItem(e.getInventory().getItem(13));
                if(lang == null) return;

                plugin.getLanguageManager().setLanguage(lang);

                player.sendMessage(
                        plugin.getLanguageManager()
                                .getMessage("language-changed","{language}",lang.toUpperCase())
                );

                player.closeInventory();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        UUID uuid = e.getWhoClicked().getUniqueId();

        if (mainGuiOpen.contains(uuid) || confirmGuiOpen.contains(uuid)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;
        UUID uuid = e.getPlayer().getUniqueId();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!mainGuiOpen.contains(uuid) && !confirmGuiOpen.contains(uuid)) {
                clearMeta((Player) e.getPlayer());
            }
        });
    }

    private String extractLangFromItem(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) return null;

        for (String line : meta.getLore()) {
            String stripped = stripColor(line).trim();
            if (stripped.startsWith("File: ") && stripped.endsWith(".yml")) {
                return stripped.substring("File: ".length()).replace(".yml", "").toLowerCase();
            }
        }
        return null;
    }

    private boolean isLanguageSlot(int slot) {
        for (int s : LANGUAGE_SLOTS) if (s == slot) return true;
        return false;
    }

    private int getPage(Player player) {
        if (player.hasMetadata("advancedduty_lang_page"))
            return player.getMetadata("advancedduty_lang_page").get(0).asInt();
        return 1;
    }

    private boolean hasNextPage(int currentPage) {
        File[] files = plugin.getLanguageManager().getLanguageFiles();
        if (files == null || files.length == 0) return false;
        int maxPage = Math.max(1, (int) Math.ceil((double) files.length / LANGUAGE_SLOTS.length));
        return currentPage < maxPage;
    }

    private void clearMeta(Player player) {
        player.removeMetadata("advancedduty_lang_select", plugin);
        player.removeMetadata("advancedduty_lang_page",   plugin);
    }

    private String stripColor(String s) {
        return s == null ? "" : s.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
    }
}