package me.hesamai.advancedduty.duty.listener;

import me.hesamai.advancedduty.Main;
import me.hesamai.advancedduty.util.CommandMatcher;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.List;

/** Configurable limits for staff: what they may not do while on duty, and which commands need duty. */
public class RestrictionListener implements Listener {

    private final Main plugin;

    public RestrictionListener(Main plugin) {
        this.plugin = plugin;
    }

    private boolean limited(Player p, String key) {
        return plugin.getDutyManager().isOnDuty(p)
                && plugin.getConfig().getBoolean("restrictions.on-duty." + key, false)
                && !p.hasPermission("advancedduty.bypass.restrictions");
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (limited(e.getPlayer(), "drop-items")) {
            e.setCancelled(true);
            plugin.getLanguageManager().send(e.getPlayer(), "restricted");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player && limited((Player) e.getEntity(), "pickup-items")) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (limited(e.getPlayer(), "block-break")) {
            e.setCancelled(true);
            plugin.getLanguageManager().send(e.getPlayer(), "restricted");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (limited(e.getPlayer(), "block-place")) {
            e.setCancelled(true);
            plugin.getLanguageManager().send(e.getPlayer(), "restricted");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player attacker = null;
        if (e.getDamager() instanceof Player) attacker = (Player) e.getDamager();
        else if (e.getDamager() instanceof org.bukkit.entity.Projectile) {
            ProjectileSource src = ((org.bukkit.entity.Projectile) e.getDamager()).getShooter();
            if (src instanceof Player) attacker = (Player) src;
        }
        if (attacker != null && limited(attacker, "pvp")) {
            e.setCancelled(true);
            plugin.getLanguageManager().send(attacker, "restricted");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onContainer(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;
        Player p = (Player) e.getPlayer();
        if (!limited(p, "containers")) return;
        InventoryType t = e.getInventory().getType();
        if (t == InventoryType.CHEST || t == InventoryType.BARREL || t == InventoryType.SHULKER_BOX
                || t == InventoryType.HOPPER || t == InventoryType.DISPENSER || t == InventoryType.DROPPER
                || t == InventoryType.FURNACE || t == InventoryType.ENDER_CHEST) {
            if (e.getInventory().getHolder() instanceof me.hesamai.advancedduty.gui.GuiHolder) return;
            e.setCancelled(true);
            plugin.getLanguageManager().send(p, "restricted");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission("advancedduty.bypass.restrictions")) return;

        if (plugin.getDutyManager().isOnDuty(p)) {
            List<String> blocked = plugin.getConfig().getStringList("restrictions.on-duty.blocked-commands");
            if (!blocked.isEmpty() && CommandMatcher.matches(e.getMessage(), blocked)) {
                e.setCancelled(true);
                plugin.getLanguageManager().send(p, "restricted-command");
            }
        } else if (p.hasPermission("advancedduty.use")) {
            List<String> needDuty = plugin.getConfig().getStringList("restrictions.off-duty.blocked-commands");
            if (!needDuty.isEmpty() && CommandMatcher.matches(e.getMessage(), needDuty)) {
                e.setCancelled(true);
                plugin.getLanguageManager().send(p, "restricted-need-duty");
            }
        }
    }
}
