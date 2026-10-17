package me.hesamai.advancedduty.duty.listener;

import me.hesamai.advancedduty.Main;
import me.hesamai.advancedduty.api.DutyCause;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Connects join / quit / world changes to the {@link me.hesamai.advancedduty.duty.DutyManager}. */
public class DutyPlayerListener implements Listener {

    private final Main plugin;

    public DutyPlayerListener(Main plugin) {
        this.plugin = plugin;
    }

    /** Loads stored data before the player enters the world (this event is already asynchronous). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        plugin.getDutyManager().preload(event.getUniqueId());
    }

    // MONITOR so that the vanish listeners (HIGH) can still read the pending session.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.getAfk().trackPlayer(event.getPlayer().getUniqueId());
        plugin.getDutyManager().handleJoin(event.getPlayer());
        plugin.getUpdates().notifyOnJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.getDutyManager().handleQuit(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player p = event.getPlayer();
        if (!plugin.getDutyManager().isOnDuty(p)) return;
        if (plugin.getConfig().getBoolean("duty.worlds.force-off", true)
                && plugin.getDutyManager().isWorldBlocked(p.getWorld())) {
            plugin.getDutyManager().disable(p, DutyCause.WORLD, null);
            plugin.getLanguageManager().send(p, "duty-world-blocked");
        }
    }
}
