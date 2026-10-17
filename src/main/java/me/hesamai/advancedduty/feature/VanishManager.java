package me.hesamai.advancedduty.feature;

import me.hesamai.advancedduty.Main;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Built-in vanish. Vanished players are hidden from everybody without {@code advancedduty.vanish.see},
 * do not trigger pressure plates, are ignored by mobs and cannot pick up items. Vanish can also be
 * delegated to another vanish plugin through commands ({@code vanish.mode: COMMAND}).
 */
public class VanishManager implements Listener {

    private final Main plugin;
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();

    public VanishManager(Main plugin) {
        this.plugin = plugin;
    }

    private String mode() {
        return plugin.getConfig().getString("vanish.mode", "BUILTIN").toUpperCase(Locale.ROOT);
    }

    public boolean isEnabled() {
        return !mode().equals("NONE");
    }

    public boolean isVanished(UUID id) {
        return vanished.contains(id);
    }

    public Set<UUID> getVanished() {
        return new java.util.HashSet<>(vanished);
    }

    /** Applies vanish to the player (idempotent). */
    public void vanish(Player p, boolean announce) {
        if (!isEnabled() || p == null) return;
        if (!vanished.add(p.getUniqueId()) && mode().equals("BUILTIN")) return;

        if (mode().equals("COMMAND")) {
            for (String c : plugin.getConfig().getStringList("vanish.commands.on")) plugin.getDutyManager().runCommand(p, c);
        } else {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other.equals(p)) continue;
                if (!other.hasPermission("advancedduty.vanish.see")) other.hidePlayer(plugin, p);
            }
            if (plugin.getConfig().getBoolean("vanish.disable-pickup", true)) p.setCanPickupItems(false);
        }
        if (announce && plugin.getConfig().getBoolean("vanish.fake-leave-message.enabled", false)) {
            Bukkit.broadcastMessage(plugin.getLanguageManager().raw("fake-leave", "{player}", p.getName()));
        }
        plugin.getLanguageManager().send(p, "vanish-on");
    }

    public void unvanish(Player p, boolean announce) {
        if (p == null) return;
        boolean was = vanished.remove(p.getUniqueId());
        if (!was) return;

        if (mode().equals("COMMAND")) {
            for (String c : plugin.getConfig().getStringList("vanish.commands.off")) plugin.getDutyManager().runCommand(p, c);
        } else {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(p)) other.showPlayer(plugin, p);
            }
            p.setCanPickupItems(true);
        }
        if (announce && plugin.getConfig().getBoolean("vanish.fake-join-message.enabled", false)) {
            Bukkit.broadcastMessage(plugin.getLanguageManager().raw("fake-join", "{player}", p.getName()));
        }
        plugin.getLanguageManager().send(p, "vanish-off");
    }

    /** Silent clean-up (quit / shutdown): nothing is announced or messaged. */
    public void release(Player p) {
        if (!vanished.remove(p.getUniqueId())) return;
        if (mode().equals("BUILTIN")) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(p)) other.showPlayer(plugin, p);
            }
            p.setCanPickupItems(true);
        }
    }

    /** Manual toggle by a staff member (command / tool). */
    public void toggleBy(Player p) {
        if (!p.hasPermission("advancedduty.vanish")) {
            plugin.getLanguageManager().send(p, "no-permission");
            return;
        }
        if (!isEnabled()) {
            plugin.getLanguageManager().send(p, "vanish-disabled");
            return;
        }
        if (plugin.getConfig().getBoolean("vanish.require-duty", true) && !plugin.getDutyManager().isOnDuty(p)) {
            plugin.getLanguageManager().send(p, "vanish-needs-duty");
            return;
        }
        if (isVanished(p.getUniqueId())) unvanish(p, true);
        else vanish(p, true);
    }

    /** Hides every vanished player from a joining non-staff player. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) {
        if (!mode().equals("BUILTIN")) return;
        Player joined = e.getPlayer();
        if (joined.hasPermission("advancedduty.vanish.see")) return;
        for (UUID id : vanished) {
            Player v = Bukkit.getPlayer(id);
            if (v != null && !v.equals(joined)) joined.hidePlayer(plugin, v);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoinMessage(PlayerJoinEvent e) {
        if (plugin.getConfig().getBoolean("vanish.silent-join-quit", true)
                && plugin.getDutyManager().willRestoreVanished(e.getPlayer().getUniqueId())) {
            e.setJoinMessage(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent e) {
        if (isVanished(e.getPlayer().getUniqueId()) && plugin.getConfig().getBoolean("vanish.silent-join-quit", true)) {
            e.setQuitMessage(null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (e.getTarget() instanceof Player && isVanished(e.getTarget().getUniqueId())
                && plugin.getConfig().getBoolean("vanish.ignore-mobs", true)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player && isVanished(e.getEntity().getUniqueId())
                && plugin.getConfig().getBoolean("vanish.disable-pickup", true)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPhysical(PlayerInteractEvent e) {
        if (e.getAction() == Action.PHYSICAL && isVanished(e.getPlayer().getUniqueId())
                && plugin.getConfig().getBoolean("vanish.ignore-pressure-plates", true)) {
            e.setCancelled(true);
        }
    }
}
