package me.hesamai.advancedduty.feature;

import me.hesamai.advancedduty.Main;
import me.hesamai.advancedduty.duty.playtime.PlaytimeFormatter;
import me.hesamai.advancedduty.duty.playtime.PlaytimeManager;
import me.hesamai.advancedduty.duty.playtime.PlaytimeManager.Period;
import me.hesamai.advancedduty.util.PapiUtil;
import me.hesamai.advancedduty.util.SoundUtil;
import me.hesamai.advancedduty.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Playtime rewards ("milestones") and the weekly staff quota.
 * <ul>
 *   <li>A goal is reached once per period (day / week / month / forever) and pays its console commands.</li>
 *   <li>The quota is a minimum amount of duty time staff should reach each period.</li>
 * </ul>
 */
public class MilestoneManager {

    private final Main plugin;
    private BukkitTask task;

    public MilestoneManager(Main plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        long every = Math.max(10, plugin.getConfig().getLong("milestones.check-seconds", 60)) * 20L;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::checkAll, every, every);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    private void checkAll() {
        if (!plugin.getConfig().getBoolean("milestones.enabled", true)) return;
        for (UUID id : plugin.getPlaytime().sessionIds()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) check(p);
        }
    }

    // ------------------------------------------------------------------ goals

    /** Pays every goal the player has reached and not yet claimed in the current period. */
    public void check(Player p) {
        if (!plugin.getConfig().getBoolean("milestones.enabled", true)) return;
        ConfigurationSection goals = plugin.getConfig().getConfigurationSection("milestones.goals");
        if (goals == null) return;
        PlaytimeManager pm = plugin.getPlaytime();
        if (pm.cached(p.getUniqueId()) == null) return;
        long now = System.currentTimeMillis();

        for (String id : goals.getKeys(false)) {
            ConfigurationSection g = goals.getConfigurationSection(id);
            if (g == null || !g.getBoolean("enabled", true)) continue;
            String perm = g.getString("permission", "");
            if (!perm.isEmpty() && !p.hasPermission(perm)) continue;

            Period period = Period.parse(g.getString("period", "WEEK"), Period.WEEK);
            long required = TimeUtil.parseDuration(g.getString("required", "10h"));
            if (required <= 0) continue;
            if (pm.get(p.getUniqueId(), period) < required) continue;

            String key = id + ":" + pm.periodKey(period, now);
            if (!pm.claim(p.getUniqueId(), key)) continue; // already paid

            pay(p, id, g, period, required);
        }
    }

    private void pay(Player p, String id, ConfigurationSection g, Period period, long required) {
        String time = PlaytimeFormatter.format(required);
        String periodName = plugin.getLanguageManager().gui("period." + period.name().toLowerCase(Locale.ROOT));

        for (String cmd : g.getStringList("commands")) {
            String c = cmd.replace("{player}", p.getName()).replace("{uuid}", p.getUniqueId().toString())
                    .replace("{goal}", id).replace("{time}", time).replace("{period}", periodName);
            c = PapiUtil.apply(p, c);
            if (c.toLowerCase(Locale.ROOT).startsWith("console:")) c = c.substring(8).trim();
            if (c.startsWith("/")) c = c.substring(1);
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c);
            } catch (Exception ex) {
                plugin.getLogger().warning("Milestone '" + id + "' command failed: " + c + " (" + ex.getMessage() + ")");
            }
        }

        String message = g.getString("message", "&aYou reached the &e{goal} &agoal ({time})!");
        if (!message.isEmpty()) {
            p.sendMessage(me.hesamai.advancedduty.util.MessageUtil.color(plugin.getLanguageManager().prefix()
                    + message.replace("{goal}", g.getString("name", id)).replace("{time}", time).replace("{period}", periodName)
                    .replace("{player}", p.getName())));
        }
        String broadcast = g.getString("broadcast", "");
        if (!broadcast.isEmpty()) {
            Bukkit.broadcastMessage(me.hesamai.advancedduty.util.MessageUtil.color(
                    broadcast.replace("{goal}", g.getString("name", id)).replace("{time}", time)
                            .replace("{period}", periodName).replace("{player}", p.getName())));
        }
        String sound = g.getString("sound", "ENTITY_PLAYER_LEVELUP");
        if (!sound.isEmpty()) SoundUtil.play(p, sound, 1f, 1f);

        plugin.getDiscord().sendEvent("milestone", p.getName(), g.getString("name", id) + " (" + time + ")");
        plugin.getLogger().info("[Milestone] " + p.getName() + " reached '" + id + "' (" + time + ", " + period + ").");
        plugin.getPlaytime().flush(p.getUniqueId(), plugin.getPlaytime().cached(p.getUniqueId()));
    }

    // ------------------------------------------------------------------ progress to the next goal

    /** {goalId -> progress 0..1} for the /duty goals command. */
    public List<String[]> describeGoals(Player p) {
        List<String[]> out = new ArrayList<>();
        ConfigurationSection goals = plugin.getConfig().getConfigurationSection("milestones.goals");
        if (goals == null) return out;
        PlaytimeManager pm = plugin.getPlaytime();
        long now = System.currentTimeMillis();
        for (String id : goals.getKeys(false)) {
            ConfigurationSection g = goals.getConfigurationSection(id);
            if (g == null || !g.getBoolean("enabled", true)) continue;
            Period period = Period.parse(g.getString("period", "WEEK"), Period.WEEK);
            long required = TimeUtil.parseDuration(g.getString("required", "10h"));
            if (required <= 0) continue;
            long have = pm.get(p.getUniqueId(), period);
            boolean done = pm.hasClaim(p.getUniqueId(), id + ":" + pm.periodKey(period, now));
            out.add(new String[]{g.getString("name", id), PlaytimeFormatter.format(Math.min(have, required)),
                    PlaytimeFormatter.format(required), String.valueOf(Math.min(100, (int) (have * 100 / required))),
                    done ? "true" : "false", period.name()});
        }
        return out;
    }

    // ------------------------------------------------------------------ quota

    public boolean isQuotaEnabled() {
        return plugin.getConfig().getBoolean("quota.enabled", false);
    }

    public Period quotaPeriod() {
        return Period.parse(plugin.getConfig().getString("quota.period", "WEEK"), Period.WEEK);
    }

    /** Required duty time for this player; 0 = exempt / no quota. */
    public long requiredFor(Player p) {
        if (!isQuotaEnabled() || p == null) return 0L;
        String exempt = plugin.getConfig().getString("quota.exempt-permission", "advancedduty.quota.exempt");
        if (!exempt.isEmpty() && p.hasPermission(exempt)) return 0L;
        ConfigurationSection overrides = plugin.getConfig().getConfigurationSection("quota.overrides");
        if (overrides != null) {
            for (String perm : overrides.getKeys(false)) {
                if (p.hasPermission(perm.replace('_', '.'))) {
                    return TimeUtil.parseDuration(overrides.getString(perm, "0"));
                }
            }
        }
        return TimeUtil.parseDuration(plugin.getConfig().getString("quota.required", "5h"));
    }

    public long requiredDefault() {
        return isQuotaEnabled() ? TimeUtil.parseDuration(plugin.getConfig().getString("quota.required", "5h")) : 0L;
    }

    /** 0..1 (capped), or -1 when there is no quota. */
    public double progress(Player p) {
        long required = requiredFor(p);
        if (required <= 0) return -1;
        long have = plugin.getPlaytime().get(p.getUniqueId(), quotaPeriod());
        return Math.min(1.0, have / (double) required);
    }

    public String bar(double fraction, int length) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, fraction)) * length);
        String full = plugin.getConfig().getString("quota.bar.full", "&a|");
        String empty = plugin.getConfig().getString("quota.bar.empty", "&7|");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) sb.append(i < filled ? full : empty);
        return sb.toString();
    }

    public void sendProgress(Player target, org.bukkit.command.CommandSender viewer) {
        long required = requiredFor(target);
        if (required <= 0) {
            plugin.getLanguageManager().send(viewer, "quota-none");
            return;
        }
        long have = plugin.getPlaytime().get(target.getUniqueId(), quotaPeriod());
        double f = Math.min(1.0, have / (double) required);
        plugin.getLanguageManager().send(viewer, f >= 1.0 ? "quota-met" : "quota-progress",
                "{player}", target.getName(),
                "{time}", PlaytimeFormatter.format(have),
                "{required}", PlaytimeFormatter.format(required),
                "{percent}", String.valueOf((int) (f * 100)),
                "{remaining}", PlaytimeFormatter.format(Math.max(0, required - have)),
                "{bar}", bar(f, 20),
                "{period}", plugin.getLanguageManager().gui("period." + quotaPeriod().name().toLowerCase(Locale.ROOT)));
    }
}
