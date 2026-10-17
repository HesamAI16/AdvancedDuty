package me.hesamai.advancedduty.gui;

import me.hesamai.advancedduty.Main;
import me.hesamai.advancedduty.api.DutyCause;
import me.hesamai.advancedduty.duty.playtime.PlaytimeFormatter;
import me.hesamai.advancedduty.duty.playtime.PlaytimeManager;
import me.hesamai.advancedduty.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Staff menu, online staff list and online player list (teleport / inspect / freeze). */
public class StaffGui implements Listener {

    private static final int PAGE_SIZE = 45;

    private final Main plugin;

    public StaffGui(Main plugin) {
        this.plugin = plugin;
    }

    private String t(String key, Object... kv) {
        return plugin.getLanguageManager().gui(key, kv);
    }

    private ItemStack filler() {
        return new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
    }

    // ------------------------------------------------------------------ menu

    public void openMenu(Player p) {
        GuiHolder holder = new GuiHolder(GuiHolder.Type.MENU, 0, null);
        Inventory inv = holder.create(27, t("menu.title"));
        for (int i = 0; i < 27; i++) inv.setItem(i, filler());

        boolean on = plugin.getDutyManager().isOnDuty(p);
        inv.setItem(10, new ItemBuilder(on ? Material.RED_DYE : Material.LIME_DYE)
                .name(on ? t("menu.duty-off") : t("menu.duty-on"))
                .lore(plugin.getLanguageManager().guiList("menu.duty-lore")).build());
        inv.setItem(11, new ItemBuilder(Material.ENDER_EYE).name(t("menu.vanish"))
                .lore(plugin.getLanguageManager().guiList("menu.vanish-lore")).build());
        inv.setItem(12, new ItemBuilder(Material.PLAYER_HEAD).name(t("menu.staff-list")).skullOwner(p).build());
        inv.setItem(13, new ItemBuilder(Material.COMPASS).name(t("menu.player-list")).build());
        inv.setItem(14, new ItemBuilder(Material.OAK_SIGN).name(t("menu.scoreboard")).build());
        inv.setItem(15, new ItemBuilder(Material.CLOCK).name(t("menu.my-stats"))
                .lore(statsLore(p)).build());
        inv.setItem(16, new ItemBuilder(Material.GOLDEN_HELMET).name(t("menu.top")).hideFlags().build());
        inv.setItem(22, new ItemBuilder(Material.BARRIER).name(t("close")).build());
        p.openInventory(inv);
    }

    private List<String> statsLore(Player p) {
        PlaytimeManager pm = plugin.getPlaytime();
        List<String> lore = new ArrayList<>();
        lore.add(t("stats.total", "{time}", PlaytimeFormatter.format(pm.get(p.getUniqueId(), PlaytimeManager.Period.ALL))));
        lore.add(t("stats.week", "{time}", PlaytimeFormatter.format(pm.get(p.getUniqueId(), PlaytimeManager.Period.WEEK))));
        lore.add(t("stats.today", "{time}", PlaytimeFormatter.format(pm.get(p.getUniqueId(), PlaytimeManager.Period.TODAY))));
        return lore;
    }

    // ------------------------------------------------------------------ lists

    public void openStaffList(Player viewer, int page) {
        List<Player> staff = new ArrayList<>();
        boolean seeVanished = viewer.hasPermission("advancedduty.vanish.see");
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (!o.hasPermission("advancedduty.use")) continue;
            if (plugin.getVanish().isVanished(o.getUniqueId()) && !seeVanished) continue;
            staff.add(o);
        }
        staff.sort(Comparator.comparing((Player o) -> !plugin.getDutyManager().isOnDuty(o)).thenComparing(Player::getName));
        page = clamp(page, staff.size());

        GuiHolder holder = new GuiHolder(GuiHolder.Type.STAFF, page, null);
        Inventory inv = holder.create(54, t("staff.title", "{count}", String.valueOf(staff.size())));
        int from = (page - 1) * PAGE_SIZE;
        for (int i = from; i < Math.min(staff.size(), from + PAGE_SIZE); i++) {
            Player o = staff.get(i);
            boolean on = plugin.getDutyManager().isOnDuty(o);
            List<String> lore = new ArrayList<>();
            lore.add(t(on ? "staff.on" : "staff.off"));
            if (on) {
                lore.add(t("staff.session", "{time}", PlaytimeFormatter.format(plugin.getPlaytime().sessionMs(o.getUniqueId()))));
                if (plugin.getAfk().isAfk(o.getUniqueId())) lore.add(t("staff.afk"));
            }
            if (plugin.getVanish().isVanished(o.getUniqueId())) lore.add(t("staff.vanished"));
            if (viewer.hasPermission("advancedduty.gui.teleport")) lore.add(t("staff.click-teleport"));
            inv.setItem(i - from, new ItemBuilder(Material.PLAYER_HEAD)
                    .name((on ? "&a" : "&7") + o.getName()).lore(lore).skullOwner(o).build());
        }
        navigation(inv, page, staff.size());
        viewer.openInventory(inv);
    }

    public void openPlayerList(Player viewer, int page) {
        List<Player> list = new ArrayList<>();
        boolean seeVanished = viewer.hasPermission("advancedduty.vanish.see");
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.equals(viewer)) continue;
            if (plugin.getVanish().isVanished(o.getUniqueId()) && !seeVanished) continue;
            list.add(o);
        }
        list.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        page = clamp(page, list.size());

        GuiHolder holder = new GuiHolder(GuiHolder.Type.PLAYERS, page, null);
        Inventory inv = holder.create(54, t("players.title", "{count}", String.valueOf(list.size())));
        int from = (page - 1) * PAGE_SIZE;
        for (int i = from; i < Math.min(list.size(), from + PAGE_SIZE); i++) {
            Player o = list.get(i);
            List<String> lore = new ArrayList<>(plugin.getLanguageManager().guiList("players.lore",
                    "{world}", o.getWorld().getName(), "{ping}", String.valueOf(o.getPing())));
            inv.setItem(i - from, new ItemBuilder(Material.PLAYER_HEAD).name("&f" + o.getName()).lore(lore).skullOwner(o).build());
        }
        navigation(inv, page, list.size());
        viewer.openInventory(inv);
    }

    private int clamp(int page, int total) {
        int max = Math.max(1, (int) Math.ceil(total / (double) PAGE_SIZE));
        return Math.max(1, Math.min(page, max));
    }

    private void navigation(Inventory inv, int page, int total) {
        for (int i = 45; i < 54; i++) inv.setItem(i, filler());
        int max = Math.max(1, (int) Math.ceil(total / (double) PAGE_SIZE));
        if (page > 1) inv.setItem(45, new ItemBuilder(Material.ARROW).name(t("previous")).build());
        inv.setItem(48, new ItemBuilder(Material.NETHER_STAR).name(t("menu.back")).build());
        inv.setItem(49, new ItemBuilder(Material.PAPER).name(t("page", "{page}", String.valueOf(page), "{max}", String.valueOf(max))).build());
        if (page < max) inv.setItem(53, new ItemBuilder(Material.ARROW).name(t("next")).build());
    }

    // ------------------------------------------------------------------ clicks

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof GuiHolder)) return;
        GuiHolder holder = (GuiHolder) e.getInventory().getHolder();
        if (holder.getType() == GuiHolder.Type.INSPECT) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player) || e.getClickedInventory() != e.getInventory()) return;
        Player p = (Player) e.getWhoClicked();
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        int slot = e.getSlot();

        switch (holder.getType()) {
            case MENU:
                menuClick(p, slot);
                break;
            case STAFF:
            case PLAYERS:
                listClick(p, holder, slot, clicked, e.getClick());
                break;
            default:
                break;
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof GuiHolder) e.setCancelled(true);
    }

    private void menuClick(Player p, int slot) {
        switch (slot) {
            case 10:
                p.closeInventory();
                if (plugin.getDutyManager().isOnDuty(p)) plugin.getDutyManager().disable(p, DutyCause.COMMAND, p);
                else plugin.getDutyManager().enable(p, null, DutyCause.COMMAND, p);
                break;
            case 11:
                p.closeInventory();
                plugin.getVanish().toggleBy(p);
                break;
            case 12:
                openStaffList(p, 1);
                break;
            case 13:
                if (p.hasPermission("advancedduty.gui.players")) openPlayerList(p, 1);
                else plugin.getLanguageManager().send(p, "no-permission");
                break;
            case 14:
                p.closeInventory();
                plugin.getDisplays().toggleScoreboard(p);
                break;
            case 16:
                p.closeInventory();
                p.performCommand("duty playtimetop");
                break;
            case 22:
                p.closeInventory();
                break;
            default:
                break;
        }
    }

    private void listClick(Player p, GuiHolder holder, int slot, ItemStack clicked, ClickType click) {
        if (slot == 45 && clicked.getType() == Material.ARROW) {
            reopen(p, holder, holder.getPage() - 1);
            return;
        }
        if (slot == 53 && clicked.getType() == Material.ARROW) {
            reopen(p, holder, holder.getPage() + 1);
            return;
        }
        if (slot == 48) {
            openMenu(p);
            return;
        }
        if (slot >= PAGE_SIZE || clicked.getType() != Material.PLAYER_HEAD) return;

        org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) clicked.getItemMeta();
        if (meta == null || meta.getOwningPlayer() == null) return;
        Player target = Bukkit.getPlayer(meta.getOwningPlayer().getUniqueId());
        if (target == null) {
            reopen(p, holder, holder.getPage());
            return;
        }

        if (holder.getType() == GuiHolder.Type.PLAYERS && click == ClickType.RIGHT && p.hasPermission("advancedduty.inspect")) {
            p.closeInventory();
            plugin.getInspect().open(p, target);
        } else if (holder.getType() == GuiHolder.Type.PLAYERS && click == ClickType.SHIFT_LEFT && p.hasPermission("advancedduty.freeze")) {
            plugin.getFreeze().toggle(p, target);
        } else if (p.hasPermission("advancedduty.gui.teleport")) {
            p.closeInventory();
            p.teleport(target.getLocation());
            plugin.getLanguageManager().send(p, "tool-teleported", "{player}", target.getName());
        }
    }

    private void reopen(Player p, GuiHolder holder, int page) {
        if (holder.getType() == GuiHolder.Type.STAFF) openStaffList(p, page);
        else openPlayerList(p, page);
    }
}
