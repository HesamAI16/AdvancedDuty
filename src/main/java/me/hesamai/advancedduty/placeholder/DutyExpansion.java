package me.hesamai.advancedduty.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.hesamai.advancedduty.Main;
import me.hesamai.advancedduty.duty.playtime.PlaytimeFormatter;
import me.hesamai.advancedduty.duty.playtime.PlaytimeManager;
import me.hesamai.advancedduty.duty.playtime.PlaytimeManager.Period;
import me.hesamai.advancedduty.feature.StaffEntry;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * PlaceholderAPI expansion. Nothing here touches the database: leaderboards come from the cached
 * snapshot and everything else from memory, so scoreboards / tab plugins can refresh every tick.
 * <pre>
 *  %advancedduty_status%  _status_color  _is_on_duty  _duty_icon  _vanished  _afk  _frozen
 *  %advancedduty_playtime%  _playtime_ms|seconds|minutes|hours   (all time)
 *  %advancedduty_playtime_today|week|month%  and  _playtime_week_ms
 *  %advancedduty_session_duration%  _session_duration_ms  _sessions  _longest_session
 *  %advancedduty_rank%  _rank_today|week|month
 *  %advancedduty_top_1_name%  _top_1_time%  _top_week_1_name%  _top_month_3_time%
 *  %advancedduty_staff_online%  _staff_online_total  _staff_list  _any_staff_online  _staff_count
 *  %advancedduty_quota_percent%  _quota_required  _quota_remaining  _quota_bar  _quota_met
 * </pre>
 */
public class DutyExpansion extends PlaceholderExpansion {

    private final Main plugin;

    public DutyExpansion(Main plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getIdentifier() { return "advancedduty"; }
    @Override public @NotNull String getAuthor() { return "hesamai"; }
    @Override public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }
    @Override public boolean canRegister() { return true; }

    @Override
    public @Nullable String onRequest(OfflinePlayer offline, @NotNull String params) {
        final String p = params.toLowerCase(Locale.ROOT);
        PlaytimeManager pm = plugin.getPlaytime();

        // ---- global placeholders
        switch (p) {
            case "staff_online": return String.valueOf(countStaff(false, true));
            case "staff_online_total": return String.valueOf(countStaff(true, true));
            case "staff_count": return String.valueOf(countStaff(false, false));
            case "any_staff_online": return String.valueOf(countStaff(false, true) > 0);
            case "staff_list": {
                List<String> names = onDutyNames();
                return names.isEmpty() ? plugin.getLanguageManager().getPlaceholder("no_staff_online") : String.join(", ", names);
            }
            case "frozen_count": return String.valueOf(plugin.getFreeze().count());
            default: break;
        }
        if (p.startsWith("top_")) return top(p);

        if (offline == null) return "";
        final UUID id = offline.getUniqueId();
        final Player online = offline.getPlayer();
        final boolean onDuty = plugin.getDutyManager().isOnDuty(id);

        switch (p) {
            case "status":
                return plugin.getLanguageManager().getPlaceholder(onDuty ? "status.on_duty" : "status.off_duty");
            case "status_color":
                return plugin.getLanguageManager().getPlaceholder(onDuty ? "status_color.on_duty" : "status_color.off_duty");
            case "is_on_duty": return String.valueOf(onDuty);
            case "duty_icon":
                return plugin.getLanguageManager().getPlaceholder(onDuty ? "duty_icon.on_duty" : "duty_icon.off_duty");
            case "has_session": return String.valueOf(pm.hasSession(id));
            case "vanished": return String.valueOf(plugin.getVanish().isVanished(id));
            case "afk": return String.valueOf(plugin.getAfk().isAfk(id));
            case "frozen": case "is_frozen": return String.valueOf(plugin.getFreeze().isFrozen(id));
            case "session_duration": return onDuty ? PlaytimeFormatter.format(pm.sessionMs(id)) : PlaytimeFormatter.format(0);
            case "session_duration_ms": return String.valueOf(onDuty ? pm.sessionMs(id) : 0L);
            case "sessions": return String.valueOf(pm.getSessions(id));
            case "longest_session": return PlaytimeFormatter.format(pm.getLongest(id));
            case "rank": return String.valueOf(pm.rank(id, Period.ALL));
            case "reason": {
                me.hesamai.advancedduty.duty.DutySession s = plugin.getDutyManager().getSession(id);
                return s == null || s.getReason() == null ? "" : s.getReason();
            }
            default: break;
        }

        // ---- quota (needs an online player for permission checks)
        if (p.startsWith("quota_")) return quota(p, online);

        // ---- playtime[_period][_unit]
        if (p.equals("playtime") || p.startsWith("playtime_")) {
            String[] parts = p.split("_");
            Period period = Period.ALL;
            String unit = "";
            for (int i = 1; i < parts.length; i++) {
                Period parsed = Period.parse(parts[i], null);
                if (parsed != null) period = parsed;
                else unit = parts[i];
            }
            long ms = pm.get(id, period);
            switch (unit) {
                case "": return PlaytimeFormatter.format(ms);
                case "ms": return String.valueOf(ms);
                case "seconds": return String.valueOf(ms / 1000);
                case "minutes": return String.valueOf(ms / 60_000);
                case "hours": return String.valueOf(ms / 3_600_000);
                default: return null;
            }
        }
        if (p.startsWith("rank_")) {
            Period period = Period.parse(p.substring(5), null);
            return period == null ? null : String.valueOf(pm.rank(id, period));
        }
        return null;
    }

    private String quota(String p, Player online) {
        if (online == null) return "";
        long required = plugin.getMilestones().requiredFor(online);
        double progress = plugin.getMilestones().progress(online);
        long have = plugin.getPlaytime().get(online.getUniqueId(), plugin.getMilestones().quotaPeriod());
        switch (p) {
            case "quota_percent": return progress < 0 ? "0" : String.valueOf((int) (progress * 100));
            case "quota_required": return required <= 0 ? "-" : PlaytimeFormatter.format(required);
            case "quota_remaining": return required <= 0 ? "-" : PlaytimeFormatter.format(Math.max(0, required - have));
            case "quota_bar": return progress < 0 ? "" : me.hesamai.advancedduty.util.MessageUtil.color(plugin.getMilestones().bar(progress, 10));
            case "quota_met": return String.valueOf(required > 0 && have >= required);
            default: return null;
        }
    }

    /** {@code top_<n>_name}, {@code top_<n>_time}, {@code top_<period>_<n>_name|time} */
    private String top(String p) {
        String[] parts = p.split("_");
        Period period = Period.ALL;
        int idx = 1;
        if (parts.length >= 4) {
            Period parsed = Period.parse(parts[1], null);
            if (parsed == null) return null;
            period = parsed;
            idx = 2;
        }
        if (parts.length < idx + 2) return null;
        int rank;
        try {
            rank = Integer.parseInt(parts[idx]);
        } catch (NumberFormatException ex) {
            return null;
        }
        String field = parts[idx + 1];
        PlaytimeManager.Row row = plugin.getPlaytime().row(period, rank);
        if (field.equals("name")) return row == null ? "-" : row.getName();
        if (field.equals("time")) return PlaytimeFormatter.format(row == null ? 0 : row.getMs());
        if (field.equals("ms")) return String.valueOf(row == null ? 0 : row.getMs());
        return null;
    }

    private int countStaff(boolean includeVanished, boolean onlyOnDuty) {
        int n = 0;
        for (StaffEntry e : plugin.getStaffList().snapshot(includeVanished)) {
            if (!onlyOnDuty || e.isOnDuty()) n++;
        }
        return n;
    }

    private List<String> onDutyNames() {
        List<String> names = new ArrayList<>();
        for (StaffEntry e : plugin.getStaffList().snapshot(false)) if (e.isOnDuty()) names.add(e.getName());
        return names;
    }
}
