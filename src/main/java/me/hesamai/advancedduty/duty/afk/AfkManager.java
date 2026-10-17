package me.hesamai.advancedduty.duty.afk;

import me.hesamai.advancedduty.Main;
import me.hesamai.advancedduty.api.DutyCause;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Detects inactive on-duty staff and pauses their playtime, takes them off duty or kicks them. */
public class AfkManager {

    private final Main plugin;
    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    private final Set<UUID> afk = ConcurrentHashMap.newKeySet();
    private final Set<UUID> warned = ConcurrentHashMap.newKeySet();
    private BukkitTask checker;

    public AfkManager(Main plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.getConfig().getBoolean("duty.afk.enabled", true)) return;
        long ticks = Math.max(1, plugin.getConfig().getLong("duty.afk.check-interval-seconds", 30)) * 20L;
        checker = Bukkit.getScheduler().runTaskTimer(plugin, this::checkAll, ticks, ticks);
    }

    public void stop() {
        if (checker != null) checker.cancel();
        checker = null;
    }

    /** Resets the idle timer (join, going on duty). Also clears a stale AFK flag. */
    public void trackPlayer(UUID uuid) {
        lastActivity.put(uuid, System.currentTimeMillis());
        afk.remove(uuid);
        warned.remove(uuid);
    }

    public void remove(UUID uuid) {
        lastActivity.remove(uuid);
        afk.remove(uuid);
        warned.remove(uuid);
    }

    public boolean isAfk(UUID uuid) {
        return afk.contains(uuid);
    }

    public void recordActivity(UUID uuid) {
        if (!plugin.getDutyManager().isOnDuty(uuid)) return;
        lastActivity.put(uuid, System.currentTimeMillis());
        warned.remove(uuid);
        if (afk.remove(uuid)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) onReturn(p);
        }
    }

    private void checkAll() {
        long timeoutMs = plugin.getConfig().getLong("duty.afk.timeout-minutes", 10) * 60_000L;
        long warnMs = plugin.getConfig().getLong("duty.afk.warn-before-seconds", 0) * 1_000L;
        long now = System.currentTimeMillis();
        String exempt = plugin.getConfig().getString("duty.afk.exempt-permission", "advancedduty.afk.exempt");

        for (UUID id : plugin.getDutyManager().getOnDutyPlayers()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            if (!exempt.isEmpty() && p.hasPermission(exempt)) continue;
            if (afk.contains(id)) continue;

            long idle = now - lastActivity.getOrDefault(id, now);
            if (idle >= timeoutMs) {
                warned.remove(id);
                afk.add(id);
                onAfk(p);
            } else if (warnMs > 0 && idle >= timeoutMs - warnMs && warned.add(id)) {
                plugin.getLanguageManager().send(p, "afk-warn");
            }
        }
    }

    private void onAfk(Player p) {
        plugin.getLanguageManager().send(p, "afk-detected");
        String action = plugin.getConfig().getString("duty.afk.action", "PAUSE").toUpperCase(Locale.ROOT);

        switch (action) {
            case "AUTO_OFF":
                plugin.getDutyManager().disable(p, DutyCause.AFK, null);
                plugin.getLanguageManager().send(p, "afk-auto-off");
                afk.remove(p.getUniqueId());
                break;
            case "KICK":
                String msg = plugin.getLanguageManager().raw("afk-kick");
                plugin.getDutyManager().disable(p, DutyCause.AFK, null);
                afk.remove(p.getUniqueId());
                p.kickPlayer(msg);
                break;
            case "PAUSE":
            default:
                plugin.getPlaytime().pause(p.getUniqueId());
                plugin.getLanguageManager().send(p, "afk-paused");
                if (plugin.getConfig().getBoolean("duty.afk.notify-staff", true)) notifyStaff(p);
                break;
        }
    }

    private void onReturn(Player p) {
        plugin.getPlaytime().resume(p.getUniqueId());
        plugin.getLanguageManager().send(p, "afk-returned");
    }

    private void notifyStaff(Player afkPlayer) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(afkPlayer)) continue;
            if (plugin.getDutyManager().isOnDuty(online) || online.hasPermission("advancedduty.staffchat.receive")) {
                plugin.getLanguageManager().send(online, "afk-staff-notify", "{player}", afkPlayer.getName());
            }
        }
    }
}
