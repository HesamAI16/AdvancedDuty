package me.hesamai.advancedduty.duty.afk;

import me.hesamai.advancedduty.Main;
import org.bukkit.event.*;
import org.bukkit.event.player.*;

public class AfkListener implements Listener {

    private final AfkManager afkManager;
    private final Main plugin;

    public AfkListener(AfkManager afkManager, Main plugin) {
        this.afkManager = afkManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!track("movement")) return;
        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
                && e.getFrom().getBlockY() == e.getTo().getBlockY()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;
        afkManager.recordActivity(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLook(PlayerMoveEvent e) {
        if (!track("look")) return;
        if (e.getFrom().getBlockX() != e.getTo().getBlockX()
                || e.getFrom().getBlockY() != e.getTo().getBlockY()
                || e.getFrom().getBlockZ() != e.getTo().getBlockZ()) return;
        if (e.getFrom().getYaw() == e.getTo().getYaw()
                && e.getFrom().getPitch() == e.getTo().getPitch()) return;
        afkManager.recordActivity(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        if (!track("chat")) return;
        afkManager.recordActivity(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!track("command")) return;
        afkManager.recordActivity(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (!track("interact")) return;
        afkManager.recordActivity(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof org.bukkit.entity.Player)) return;
        if (!track("inventory")) return;
        afkManager.recordActivity(e.getWhoClicked().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent e) {
        if (!track("sneak")) return;
        afkManager.recordActivity(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSprint(PlayerToggleSprintEvent e) {
        if (!track("sprint")) return;
        afkManager.recordActivity(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwingArm(PlayerAnimationEvent e) {
        if (!track("swing")) return;
        afkManager.recordActivity(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        afkManager.remove(e.getPlayer().getUniqueId());
    }

    private boolean track(String key) {
        return plugin.getConfig().getBoolean("duty.afk.track." + key, defaultFor(key));
    }

    private boolean defaultFor(String key) {
        switch (key) {
            case "movement":  return true;
            case "chat":      return true;
            case "command":   return true;
            case "interact":  return true;
            case "look":      return false;
            case "inventory": return false;
            case "sneak":     return false;
            case "sprint":    return false;
            case "swing":     return false;
            default:          return true;
        }
    }
}