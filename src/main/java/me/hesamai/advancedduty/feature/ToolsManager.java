package me.hesamai.advancedduty.feature;

import me.hesamai.advancedduty.Main;
import me.hesamai.advancedduty.api.DutyCause;
import me.hesamai.advancedduty.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hotbar items every staff member receives while on duty (vanish toggle, freeze wand, inspector...).
 * The items are tagged, can never be dropped / moved / stored, and are removed again before the
 * on-duty inventory is saved, so they can never be duplicated or left behind.
 */
public class ToolsManager implements Listener {

    public static final String TAG = "tool";

    private final Main plugin;
    private final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();

    public ToolsManager(Main plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("tools.enabled", true);
    }

    public String toolId(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return null;
        return ItemBuilder.readTag(plugin, stack, TAG);
    }

    public boolean isTool(ItemStack stack) {
        return toolId(stack) != null;
    }

    // ------------------------------------------------------------------ give / strip

    public void give(Player player, String setName) {
        if (!isEnabled() || setName == null || setName.equalsIgnoreCase("none")) return;
        ConfigurationSection items = plugin.getConfig().getConfigurationSection("tools.sets." + setName + ".items");
        if (items == null) {
            if (!"default".equalsIgnoreCase(setName)) give(player, "default");
            return;
        }
        PlayerInventory inv = player.getInventory();
        for (String id : items.getKeys(false)) {
            ConfigurationSection sec = items.getConfigurationSection(id);
            if (sec == null || !sec.getBoolean("enabled", true)) continue;
            String perm = sec.getString("permission", "");
            if (!perm.isEmpty() && !player.hasPermission(perm)) continue;

            int slot = Math.max(0, Math.min(35, sec.getInt("slot", 0)));
            ItemStack existing = inv.getItem(slot);
            if (existing != null && id.equals(toolId(existing))) continue; // already there (rejoin)
            removeTool(player, id);

            ItemStack tool = new ItemBuilder(ItemBuilder.material(sec.getString("material", "STICK"), Material.STICK))
                    .name(sec.getString("name", "&f" + id))
                    .lore(sec.getStringList("lore"))
                    .glow(sec.getBoolean("glow", false))
                    .hideFlags()
                    .tag(plugin, TAG, id)
                    .build();

            if (existing != null && existing.getType() != Material.AIR && !isTool(existing)) {
                Map<Integer, ItemStack> left = inv.addItem(existing);
                for (ItemStack rest : left.values()) player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
            inv.setItem(slot, tool);
        }
        player.updateInventory();
    }

    private void removeTool(Player player, String id) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it != null && id.equals(toolId(it))) inv.setItem(i, null);
        }
    }

    /** Removes every staff tool from the player's inventory, armor, off hand and cursor. */
    public void strip(Player player) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (isTool(it)) inv.setItem(i, null);
        }
        if (isTool(inv.getItemInOffHand())) inv.setItemInOffHand(null);
        ItemStack[] armor = inv.getArmorContents();
        boolean changed = false;
        for (int i = 0; i < armor.length; i++) {
            if (isTool(armor[i])) {
                armor[i] = null;
                changed = true;
            }
        }
        if (changed) inv.setArmorContents(armor);
        if (isTool(player.getItemOnCursor())) player.setItemOnCursor(null);
        player.updateInventory();
    }

    // ------------------------------------------------------------------ protection

    private boolean protect() {
        return plugin.getConfig().getBoolean("tools.protect", true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (protect() && isTool(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (protect() && (isTool(e.getMainHandItem()) || isTool(e.getOffHandItem()))) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (isTool(e.getItemInHand())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!protect()) return;
        boolean tool = isTool(e.getCurrentItem()) || isTool(e.getCursor());
        if (!tool && e.getClick().name().contains("NUMBER_KEY") && e.getWhoClicked() instanceof Player) {
            ItemStack hot = ((Player) e.getWhoClicked()).getInventory().getItem(e.getHotbarButton());
            tool = isTool(hot);
        }
        if (!tool && e.getClick().name().equals("SWAP_OFFHAND")) {
            tool = isTool(((Player) e.getWhoClicked()).getInventory().getItemInOffHand());
        }
        if (tool) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (protect() && isTool(e.getOldCursor())) e.setCancelled(true);
    }

    /** Tools of players who are not on duty (server crash, plugin removed...) disappear on join. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && !plugin.getDutyManager().isOnDuty(p) && !plugin.getDutyManager().isRestoring(p.getUniqueId())) {
                strip(p);
            }
        }, 10L);
    }

    // ------------------------------------------------------------------ use

    @EventHandler(ignoreCancelled = false)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() == Action.PHYSICAL) return;
        String id = toolId(e.getItem());
        if (id == null) return;
        e.setCancelled(true);

        Player p = e.getPlayer();
        ConfigurationSection sec = section(p, id);
        if (sec == null) return;
        boolean left = e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK;
        String action = sec.getString(left ? "left-click" : "action", sec.getString("action", "NONE"));
        if (left && !sec.contains("left-click")) return;
        run(p, sec, action, null);
    }

    @EventHandler(ignoreCancelled = false)
    public void onUseEntity(PlayerInteractEntityEvent e) {
        if (e instanceof PlayerInteractAtEntityEvent) return; // fires twice - handle only the plain one
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        String id = toolId(p.getInventory().getItemInMainHand());
        if (id == null) return;
        e.setCancelled(true);
        ConfigurationSection sec = section(p, id);
        if (sec == null) return;
        Player target = e.getRightClicked() instanceof Player ? (Player) e.getRightClicked() : null;
        run(p, sec, sec.getString("action", "NONE"), target);
    }

    private ConfigurationSection section(Player p, String id) {
        String set = plugin.getDutyManager().toolSetOf(p);
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("tools.sets." + set + ".items." + id);
        if (s == null) s = plugin.getConfig().getConfigurationSection("tools.sets.default.items." + id);
        return s;
    }

    private void run(Player p, ConfigurationSection sec, String rawAction, Player target) {
        long now = System.currentTimeMillis();
        Long last = lastUse.get(p.getUniqueId());
        long cd = sec.getLong("cooldown-ms", 400);
        if (last != null && now - last < cd) return;
        lastUse.put(p.getUniqueId(), now);

        if (!plugin.getDutyManager().isOnDuty(p)) {
            strip(p);
            return;
        }
        String action = rawAction == null ? "NONE" : rawAction.toUpperCase(Locale.ROOT);
        switch (action) {
            case "VANISH":
                plugin.getVanish().toggleBy(p);
                break;
            case "FLY": {
                boolean now2 = !p.getAllowFlight();
                p.setAllowFlight(now2);
                if (!now2) p.setFlying(false);
                plugin.getLanguageManager().send(p, now2 ? "tool-fly-on" : "tool-fly-off");
                break;
            }
            case "NIGHT_VISION": {
                org.bukkit.potion.PotionEffectType nv = org.bukkit.potion.PotionEffectType.NIGHT_VISION;
                if (p.hasPotionEffect(nv)) p.removePotionEffect(nv);
                else p.addPotionEffect(new org.bukkit.potion.PotionEffect(nv, Integer.MAX_VALUE, 0, false, false, true));
                break;
            }
            case "DUTY_OFF":
                plugin.getDutyManager().disable(p, DutyCause.TOOL, p);
                break;
            case "FREEZE":
                if (target == null) {
                    plugin.getLanguageManager().send(p, "tool-click-player");
                } else if (p.hasPermission("advancedduty.freeze")) {
                    plugin.getFreeze().toggle(p, target);
                } else {
                    plugin.getLanguageManager().send(p, "no-permission");
                }
                break;
            case "INSPECT":
                if (target == null) plugin.getLanguageManager().send(p, "tool-click-player");
                else if (p.hasPermission("advancedduty.inspect")) plugin.getInspect().open(p, target);
                else plugin.getLanguageManager().send(p, "no-permission");
                break;
            case "RANDOM_TP":
                randomTeleport(p);
                break;
            case "STAFF_LIST":
                plugin.getStaffGui().openStaffList(p, 1);
                break;
            case "PLAYER_LIST":
                plugin.getStaffGui().openPlayerList(p, 1);
                break;
            case "MENU":
                plugin.getStaffGui().openMenu(p);
                break;
            case "COMMAND": {
                for (String c : sec.getStringList("commands")) plugin.getDutyManager().runCommand(p, c);
                String single = sec.getString("command", "");
                if (!single.isEmpty()) plugin.getDutyManager().runCommand(p, single);
                break;
            }
            default:
                break;
        }
    }

    private void randomTeleport(Player p) {
        if (!p.hasPermission("advancedduty.randomtp")) {
            plugin.getLanguageManager().send(p, "no-permission");
            return;
        }
        List<Player> candidates = new ArrayList<>();
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.equals(p) || plugin.getDutyManager().isOnDuty(o)) continue;
            if (plugin.getVanish().isVanished(o.getUniqueId())) continue;
            candidates.add(o);
        }
        if (candidates.isEmpty()) {
            plugin.getLanguageManager().send(p, "tool-no-players");
            return;
        }
        Player t = candidates.get(new java.util.Random().nextInt(candidates.size()));
        p.teleport(t.getLocation());
        plugin.getLanguageManager().send(p, "tool-teleported", "{player}", t.getName());
    }
}
