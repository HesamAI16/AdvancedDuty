package me.hesamai.advancedduty.duty.afk;

import me.hesamai.advancedduty.Main;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AfkManager {

    private final Main plugin;
    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> afkStatus = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> warnedPlayers = new ConcurrentHashMap<>();
    private BukkitTask checkerTask;

    public AfkManager(Main plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("duty.afk.enabled", true)) return;
        long intervalTicks = plugin.getConfig().getLong("duty.afk.check-interval-seconds", 30) * 20L;
        checkerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkAll, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (checkerTask != null) {
            checkerTask.cancel();
            checkerTask = null;
        }
    }

    public void trackPlayer(UUID uuid) {
        lastActivity.put(uuid, System.currentTimeMillis());
    }

    public void remove(UUID uuid) {
        lastActivity.remove(uuid);
        afkStatus.remove(uuid);
        warnedPlayers.remove(uuid);
    }

    public boolean isAfk(UUID uuid) {
        return Boolean.TRUE.equals(afkStatus.get(uuid));
    }

    public void recordActivity(UUID uuid) {
        lastActivity.put(uuid, System.currentTimeMillis());
        warnedPlayers.remove(uuid);
        if (Boolean.TRUE.equals(afkStatus.get(uuid))) {
            afkStatus.put(uuid, false);
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) onReturn(p);
        }
    }

    private void checkAll() {
        long timeoutMs = plugin.getConfig().getLong("duty.afk.timeout-minutes", 10) * 60_000L;
        long warnBeforeMs = plugin.getConfig().getLong("duty.afk.warn-before-seconds", 60) * 1_000L;

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!plugin.getDutyManager().isOnDuty(p)) continue;

            UUID uuid = p.getUniqueId();
            long idleMs = System.currentTimeMillis() - lastActivity.getOrDefault(uuid, System.currentTimeMillis());
            boolean alreadyAfk = isAfk(uuid);

            if (!alreadyAfk && idleMs >= timeoutMs) {
                warnedPlayers.remove(uuid);
                afkStatus.put(uuid, true);
                onAfk(p);
            } else if (!alreadyAfk && warnBeforeMs > 0
                    && !Boolean.TRUE.equals(warnedPlayers.get(uuid))
                    && idleMs >= timeoutMs - warnBeforeMs) {
                warnedPlayers.put(uuid, true);
                p.sendMessage(plugin.getLanguageManager().getMessage("afk-warn"));
            }
        }
    }

    private void onAfk(Player p) {
        p.sendMessage(plugin.getLanguageManager().getMessage("afk-detected"));

        String action = plugin.getConfig().getString("duty.afk.action", "PAUSE").toUpperCase();

        switch (action) {
            case "AUTO_OFF":
                plugin.getDutyManager().disableDuty(p);
                p.sendMessage(plugin.getLanguageManager().getMessage("afk-auto-off"));
                break;

            case "KICK":
                String kickMsg = plugin.getLanguageManager().getMessage("afk-kick");
                Bukkit.getScheduler().runTask(plugin, () -> p.kickPlayer(kickMsg));
                break;

            case "PAUSE":
            default:
                plugin.getPlaytimeManager().pauseSession(p.getUniqueId());
                p.sendMessage(plugin.getLanguageManager().getMessage("afk-paused"));

                if (plugin.getConfig().getBoolean("duty.afk.notify-staff", true)) {
                    broadcastStaff(p, "afk-staff-notify");
                }
                break;
        }
    }

    private void onReturn(Player p) {
        String action = plugin.getConfig().getString("duty.afk.action", "PAUSE").toUpperCase();
        if (action.equals("PAUSE")) {
            plugin.getPlaytimeManager().resumeSession(p.getUniqueId());
            p.sendMessage(plugin.getLanguageManager().getMessage("afk-returned"));
        }
    }

    private void broadcastStaff(Player afkPlayer, String msgKey) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("{player}", afkPlayer.getName());
        String msg = plugin.getLanguageManager().getMessage(msgKey, placeholders);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (plugin.getDutyManager().isOnDuty(online) || online.hasPermission("advancedduty.staffchat.receive")) {
                online.sendMessage(msg);
            }
        }
    }
}