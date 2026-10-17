package me.hesamai.advancedduty.feature;

import me.hesamai.advancedduty.Main;
import me.hesamai.advancedduty.duty.playtime.PlaytimeFormatter;
import me.hesamai.advancedduty.duty.playtime.PlaytimeManager.Period;
import me.hesamai.advancedduty.duty.profile.ProfileConfig;
import me.hesamai.advancedduty.util.MessageUtil;
import me.hesamai.advancedduty.util.PapiUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.scheduler.BukkitTask;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Everything staff see about their own duty state: sidebar scoreboard, boss bar, action bar and tab
 * list name. The scoreboard can be switched off per player ({@code /duty scoreboard}).
 */
public class DisplayManager {

    private static final class Board {
        Scoreboard previous;
        Scoreboard board;
        Objective objective;
        final List<Team> teams = new ArrayList<>();
        String layout;
    }

    private final Main plugin;
    private final Map<UUID, Board> boards = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();
    private final Map<UUID, ProfileConfig> profiles = new ConcurrentHashMap<>();
    private final NamespacedKey scoreboardOff;
    private BukkitTask task;
    private int cycle;

    public DisplayManager(Main plugin) {
        this.plugin = plugin;
        this.scoreboardOff = new NamespacedKey(plugin, "scoreboard_off");
    }

    public void start() {
        stop();
        long ticks = Math.max(5, plugin.getConfig().getLong("display.update-ticks", 20));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, ticks, ticks);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    public void shutdown() {
        stop();
        for (UUID id : new ArrayList<>(profiles.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) end(p);
        }
        boards.clear();
        bars.clear();
        profiles.clear();
    }

    // ------------------------------------------------------------------ start / stop for a player

    public void begin(Player p, ProfileConfig profile) {
        profiles.put(p.getUniqueId(), profile);
        if (!profile.getTabPrefix().isEmpty() || !profile.getTabSuffix().isEmpty()) {
            p.setPlayerListName(MessageUtil.color(profile.getTabPrefix()) + p.getName() + MessageUtil.color(profile.getTabSuffix()));
        }
        if (plugin.getConfig().getBoolean("display.bossbar.enabled", false)) {
            BossBar bar = Bukkit.createBossBar("", parseColor(plugin.getConfig().getString("display.bossbar.color", "GREEN")),
                    parseStyle(plugin.getConfig().getString("display.bossbar.style", "SOLID")));
            bar.addPlayer(p);
            bars.put(p.getUniqueId(), bar);
        }
        refresh(p);
    }

    public void end(Player p) {
        UUID id = p.getUniqueId();
        profiles.remove(id);
        BossBar bar = bars.remove(id);
        if (bar != null) bar.removeAll();
        Board b = boards.remove(id);
        if (b != null) {
            Scoreboard restore = b.previous != null ? b.previous : Bukkit.getScoreboardManager().getMainScoreboard();
            if (p.getScoreboard() == b.board) p.setScoreboard(restore);
        }
        if (p.isOnline()) p.setPlayerListName(null);
    }

    // ------------------------------------------------------------------ scoreboard preference

    public boolean isScoreboardHidden(Player p) {
        Byte v = p.getPersistentDataContainer().get(scoreboardOff, PersistentDataType.BYTE);
        return v != null && v == 1;
    }

    public void toggleScoreboard(Player p) {
        boolean nowHidden = !isScoreboardHidden(p);
        p.getPersistentDataContainer().set(scoreboardOff, PersistentDataType.BYTE, (byte) (nowHidden ? 1 : 0));
        plugin.getLanguageManager().send(p, nowHidden ? "scoreboard-hidden" : "scoreboard-shown");
        if (nowHidden) {
            Board b = boards.remove(p.getUniqueId());
            if (b != null && p.getScoreboard() == b.board) {
                p.setScoreboard(b.previous != null ? b.previous : Bukkit.getScoreboardManager().getMainScoreboard());
            }
        } else if (plugin.getDutyManager().isOnDuty(p)) {
            refresh(p);
        }
    }

    // ------------------------------------------------------------------ refresh

    private void refreshAll() {
        cycle++;
        for (UUID id : new ArrayList<>(profiles.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) {
                profiles.remove(id);
                continue;
            }
            refresh(p);
        }
    }

    private void refresh(Player p) {
        ProfileConfig profile = profiles.get(p.getUniqueId());
        if (profile == null) return;
        try {
            updateScoreboard(p, profile);
            updateBossBar(p);
            long interval = Math.max(1, plugin.getConfig().getLong("display.actionbar.interval-ticks", 40)
                    / Math.max(1, plugin.getConfig().getLong("display.update-ticks", 20)));
            if (plugin.getConfig().getBoolean("display.actionbar.enabled", false) && cycle % interval == 0) {
                String text = plugin.getConfig().getString("display.actionbar.text", "&aON DUTY &7- &f{duty_time}");
                MessageUtil.sendActionBar(p, fill(p, text));
            }
        } catch (Throwable ex) {
            plugin.getLogger().warning("Display update failed for " + p.getName() + ": " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ scoreboard

    private void updateScoreboard(Player p, ProfileConfig profile) {
        if (!plugin.getConfig().getBoolean("display.scoreboard.enabled", false) || isScoreboardHidden(p)) return;

        String layoutName = profile.getScoreboardLayout();
        ConfigurationSection layout = plugin.getConfig().getConfigurationSection("display.scoreboard.layouts." + layoutName);
        if (layout == null) {
            layoutName = "default";
            layout = plugin.getConfig().getConfigurationSection("display.scoreboard.layouts.default");
        }
        if (layout == null) return;

        List<String> lines = layout.getStringList("lines");
        if (lines.size() > 15) lines = lines.subList(0, 15);

        Board b = boards.get(p.getUniqueId());
        if (b == null || !layoutName.equals(b.layout) || b.teams.size() != lines.size()) {
            b = build(p, b, layoutName, lines.size());
        }
        String title = fill(p, layout.getString("title", "&6&lDUTY"));
        b.objective.setDisplayName(title.length() > 128 ? title.substring(0, 128) : title);

        for (int i = 0; i < lines.size(); i++) {
            String text = fill(p, lines.get(i));
            Team t = b.teams.get(i);
            if (text.length() <= 64) {
                t.setPrefix(text);
                t.setSuffix("");
            } else {
                int cut = text.charAt(63) == ChatColor.COLOR_CHAR ? 63 : 64;
                t.setPrefix(text.substring(0, cut));
                String rest = ChatColor.getLastColors(text.substring(0, cut)) + text.substring(cut);
                t.setSuffix(rest.length() > 64 ? rest.substring(0, 64) : rest);
            }
        }
        if (p.getScoreboard() != b.board) p.setScoreboard(b.board);
    }

    private Board build(Player p, Board old, String layoutName, int lineCount) {
        Board b = new Board();
        b.previous = old != null ? old.previous : p.getScoreboard();
        if (b.previous != null && isOwn(b.previous)) b.previous = Bukkit.getScoreboardManager().getMainScoreboard();
        b.layout = layoutName;
        b.board = Bukkit.getScoreboardManager().getNewScoreboard();
        b.objective = b.board.registerNewObjective("adduty", "dummy", "duty");
        b.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        for (int i = 0; i < lineCount; i++) {
            String entry = ChatColor.values()[i % 16].toString() + ChatColor.RESET + (i >= 16 ? ChatColor.WHITE : "");
            Team t = b.board.registerNewTeam("l" + i);
            t.addEntry(entry);
            b.objective.getScore(entry).setScore(lineCount - i);
            b.teams.add(t);
        }
        boards.put(p.getUniqueId(), b);
        return b;
    }

    private boolean isOwn(Scoreboard sb) {
        for (Board b : boards.values()) if (b.board == sb) return true;
        return false;
    }

    // ------------------------------------------------------------------ boss bar

    private void updateBossBar(Player p) {
        BossBar bar = bars.get(p.getUniqueId());
        if (bar == null) return;
        bar.setTitle(fill(p, plugin.getConfig().getString("display.bossbar.text", "&aON DUTY &7- &f{duty_time}")));
        String mode = plugin.getConfig().getString("display.bossbar.progress", "QUOTA").toUpperCase(Locale.ROOT);
        double progress = 1.0;
        if (mode.equals("QUOTA")) {
            double q = plugin.getMilestones().progress(p);
            progress = q < 0 ? 1.0 : q;
        }
        bar.setProgress(Math.max(0, Math.min(1, progress)));
    }

    private static BarColor parseColor(String raw) {
        try {
            return BarColor.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            return BarColor.GREEN;
        }
    }

    private static BarStyle parseStyle(String raw) {
        try {
            return BarStyle.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            return BarStyle.SOLID;
        }
    }

    // ------------------------------------------------------------------ text

    /** Replaces the built-in tokens, then PlaceholderAPI, then colours. */
    public String fill(Player p, String text) {
        if (text == null) return "";
        UUID id = p.getUniqueId();
        long total = plugin.getPlaytime().get(id, Period.ALL);
        double quota = plugin.getMilestones().progress(p);
        long required = plugin.getMilestones().requiredFor(p);
        int staffOnline = 0;
        for (Player o : Bukkit.getOnlinePlayers()) if (plugin.getDutyManager().isOnDuty(o)
                && (!plugin.getVanish().isVanished(o.getUniqueId()) || p.hasPermission("advancedduty.vanish.see"))) staffOnline++;

        String out = text
                .replace("{player}", p.getName())
                .replace("{displayname}", p.getDisplayName())
                .replace("{duty_time}", PlaytimeFormatter.format(plugin.getPlaytime().sessionMs(id)))
                .replace("{total}", PlaytimeFormatter.format(total))
                .replace("{today}", PlaytimeFormatter.format(plugin.getPlaytime().get(id, Period.TODAY)))
                .replace("{week}", PlaytimeFormatter.format(plugin.getPlaytime().get(id, Period.WEEK)))
                .replace("{month}", PlaytimeFormatter.format(plugin.getPlaytime().get(id, Period.MONTH)))
                .replace("{rank}", String.valueOf(plugin.getPlaytime().rank(id, Period.ALL)))
                .replace("{rank_week}", String.valueOf(plugin.getPlaytime().rank(id, Period.WEEK)))
                .replace("{staff_online}", String.valueOf(staffOnline))
                .replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("{max}", String.valueOf(Bukkit.getMaxPlayers()))
                .replace("{tps}", tps())
                .replace("{ping}", String.valueOf(p.getPing()))
                .replace("{world}", p.getWorld().getName())
                .replace("{vanished}", plugin.getLanguageManager().gui(plugin.getVanish().isVanished(id) ? "yes" : "no"))
                .replace("{afk}", plugin.getLanguageManager().gui(plugin.getAfk().isAfk(id) ? "yes" : "no"))
                .replace("{frozen}", String.valueOf(plugin.getFreeze().count()))
                .replace("{quota}", quota < 0 ? "-" : String.valueOf((int) (quota * 100)))
                .replace("{quota_bar}", quota < 0 ? "" : plugin.getMilestones().bar(quota, 10))
                .replace("{quota_required}", required <= 0 ? "-" : PlaytimeFormatter.format(required))
                .replace("{date}", new SimpleDateFormat("yyyy-MM-dd").format(new Date()))
                .replace("{time}", new SimpleDateFormat("HH:mm").format(new Date()));
        out = PapiUtil.apply(p, out);
        return MessageUtil.color(out);
    }

    /** Paper exposes Bukkit.getTPS(); Spigot 1.19.4 does not have it in the API, so it is looked up reflectively. */
    private static double[] tpsArray() throws Exception {
        try {
            return (double[]) Bukkit.class.getMethod("getTPS").invoke(null);
        } catch (NoSuchMethodException ex) {
            Object spigot = Bukkit.getServer().getClass().getMethod("spigot").invoke(Bukkit.getServer());
            return (double[]) spigot.getClass().getMethod("getTPS").invoke(spigot);
        }
    }

    private String tps() {
        try {
            double[] t = tpsArray();
            return String.format(Locale.ROOT, "%.1f", Math.min(20.0, t[0]));
        } catch (Throwable ex) {
            return "20.0";
        }
    }
}
