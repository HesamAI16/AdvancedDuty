package me.hesamai.advancedduty.command;

import me.hesamai.advancedduty.Main;
import me.hesamai.advancedduty.api.DutyCause;
import me.hesamai.advancedduty.api.DutyResult;
import me.hesamai.advancedduty.duty.DutySession;
import me.hesamai.advancedduty.duty.history.SessionRecord;
import me.hesamai.advancedduty.duty.playtime.PlaytimeFormatter;
import me.hesamai.advancedduty.duty.playtime.PlaytimeManager;
import me.hesamai.advancedduty.duty.playtime.PlaytimeManager.Period;
import me.hesamai.advancedduty.feature.StaffEntry;
import me.hesamai.advancedduty.lang.LanguageManager;
import me.hesamai.advancedduty.lang.gui.LanguageGUI;
import me.hesamai.advancedduty.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** {@code /duty} and all of its sub-commands. */
public class DutyCommand implements CommandExecutor, TabCompleter {

    private static final String[][] SUBS = {
            // sub-command, permission ("" = everybody)
            {"on", "advancedduty.use"}, {"off", "advancedduty.use"}, {"toggle", "advancedduty.use"},
            {"status", "advancedduty.status"}, {"playtime", "advancedduty.playtime"},
            {"playtimetop", "advancedduty.playtime.top"}, {"log", "advancedduty.log"},
            {"list", "advancedduty.stafflist"}, {"goals", "advancedduty.goals"}, {"quota", "advancedduty.quota"},
            {"vanish", "advancedduty.vanish"}, {"freeze", "advancedduty.freeze"}, {"unfreeze", "advancedduty.freeze"},
            {"inspect", "advancedduty.inspect"}, {"echest", "advancedduty.inspect"}, {"gui", "advancedduty.gui"},
            {"scoreboard", "advancedduty.scoreboard"}, {"staffchat", "advancedduty.staffchat"},
            {"reset", "advancedduty.playtime.reset"}, {"reload", "advancedduty.reload"},
            {"languages", "advancedduty.languages"}, {"update", "advancedduty.update"},
            {"info", "advancedduty.reload"}, {"help", ""}, {"version", ""}
    };

    private final Main plugin;
    private final LanguageManager lang;
    private final Map<UUID, String[]> resetConfirm = new HashMap<>();

    public DutyCommand(Main plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }

    private LanguageManager lang() {
        return plugin.getLanguageManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        try {
            switch (sub) {
                case "reload": return reload(sender);
                case "version": return version(sender);
                case "info": return info(sender);
                case "help": case "?": return help(sender, label);
                case "playtimetop": case "top": case "leaderboard": top(sender, args); return true;
                case "playtime": case "pt": playtime(sender, args); return true;
                case "status": status(sender, args); return true;
                case "reset": reset(sender, args, label); return true;
                case "languages": case "language": case "lang": return languages(sender);
                case "update": return update(sender);
                case "log": case "history": log(sender, args); return true;
                case "list": case "staff": staffList(sender); return true;
                case "goals": case "milestones": goals(sender); return true;
                case "quota": quota(sender, args); return true;
                case "vanish": case "v": vanish(sender); return true;
                case "freeze": case "ss": freeze(sender, args, true); return true;
                case "unfreeze": freeze(sender, args, false); return true;
                case "inspect": case "invsee": inspect(sender, args, false); return true;
                case "echest": case "enderchest": inspect(sender, args, true); return true;
                case "gui": case "menu": gui(sender); return true;
                case "scoreboard": case "sb": scoreboard(sender); return true;
                case "staffchat": case "sc": staffChat(sender, args); return true;
                default: return dutyToggle(sender, label, args, sub);
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Error while running /" + label + " " + String.join(" ", args), ex);
            lang().send(sender, "error");
            return true;
        }
    }

    // ------------------------------------------------------------------ helpers

    private boolean noPerm(CommandSender s, String perm) {
        if (perm.isEmpty() || s.hasPermission(perm)) return false;
        lang().send(s, "no-permission");
        return true;
    }

    private Player playerOnly(CommandSender s) {
        if (s instanceof Player) return (Player) s;
        lang().sendRaw(s, "only-player");
        return null;
    }

    private boolean visibleTo(CommandSender viewer, Player target) {
        return !plugin.getVanish().isVanished(target.getUniqueId()) || viewer.hasPermission("advancedduty.vanish.see")
                || (viewer instanceof Player && viewer.equals(target));
    }

    private Player onlineTarget(CommandSender sender, String name) {
        Player p = Bukkit.getPlayerExact(name);
        if (p == null) p = Bukkit.getPlayer(name);
        if (p == null || !visibleTo(sender, p)) {
            lang().send(sender, "unknown-player");
            return null;
        }
        return p;
    }

    private void runSync(Runnable r) {
        Bukkit.getScheduler().runTask(plugin, r);
    }

    private static String join(String[] args, int from) {
        return from >= args.length ? "" : String.join(" ", Arrays.copyOfRange(args, from, args.length));
    }

    private ZoneId zone() {
        return plugin.getPlaytime().zone();
    }

    private String periodName(Period p) {
        return lang().gui("period." + p.name().toLowerCase(Locale.ROOT));
    }

    // ------------------------------------------------------------------ on / off / toggle

    private boolean dutyToggle(CommandSender sender, String label, String[] args, String sub) {
        if (!sub.isEmpty() && !sub.equals("on") && !sub.equals("off") && !sub.equals("toggle")) {
            lang().send(sender, "usage-duty", "{label}", label);
            return true;
        }
        Player self = playerOnly(sender);
        if (self == null) return true;
        if (noPerm(sender, "advancedduty.use")) return true;

        // arguments: [player] [reason...] with an optional -s (silent) flag
        List<String> rest = new ArrayList<>(Arrays.asList(args).subList(Math.min(1, args.length), args.length));
        boolean silent = false;
        if (!rest.isEmpty() && rest.get(0).equalsIgnoreCase("-s")) {
            if (sender.hasPermission("advancedduty.silent")) {
                silent = true;
                rest.remove(0);
            }
        }
        Player target = self;
        if (!rest.isEmpty() && sender.hasPermission("advancedduty.use.others")) {
            Player possible = Bukkit.getPlayerExact(rest.get(0));
            if (possible != null && visibleTo(sender, possible)) {
                target = possible;
                rest.remove(0);
                if (!rest.isEmpty() && rest.get(0).equalsIgnoreCase("-s") && sender.hasPermission("advancedduty.silent")) {
                    silent = true;
                    rest.remove(0);
                }
            }
        }
        String reason = String.join(" ", rest).trim();
        boolean other = !target.equals(self);

        if (other && noPerm(sender, "advancedduty.use.others")) return true;
        if (other && !target.hasPermission("advancedduty.use")) {
            lang().send(sender, "target-no-duty-access", "{player}", target.getName());
            return true;
        }

        boolean on = sub.equals("on") || (sub.isEmpty() || sub.equals("toggle")) && !plugin.getDutyManager().isOnDuty(target);
        if (sub.equals("off")) on = false;

        if (on && !plugin.getDutyManager().isOnDuty(target)) {
            boolean required = plugin.getConfig().getBoolean("duty.reasons.required", plugin.getConfig().getBoolean("duty.require-reason", false));
            int min = plugin.getConfig().getInt("duty.reasons.min-length", 0);
            if (!other && required && !sender.hasPermission("advancedduty.reason.bypass") && reason.length() < Math.max(1, min)) {
                lang().send(sender, "usage-duty-reason", "{label}", label);
                return true;
            }
            int maxLen = plugin.getConfig().getInt("duty.reasons.max-length", 100);
            if (reason.length() > maxLen) reason = reason.substring(0, maxLen);
        }

        DutyResult result = on
                ? plugin.getDutyManager().enable(target, reason.isEmpty() ? null : reason, other ? DutyCause.ADMIN : DutyCause.COMMAND, sender, silent)
                : plugin.getDutyManager().disable(target, other ? DutyCause.ADMIN : DutyCause.COMMAND, sender, silent);
        reportResult(sender, target, on, result, other);
        return true;
    }

    private void reportResult(CommandSender sender, Player target, boolean on, DutyResult result, boolean other) {
        switch (result) {
            case SUCCESS:
                lang().send(target, on ? "duty-enabled" : "duty-disabled");
                if (other) lang().send(sender, on ? "duty-enabled-other" : "duty-disabled-other", "{player}", target.getName());
                break;
            case ALREADY_IN_STATE:
                if (other) lang().send(sender, on ? "already-on-duty-other" : "already-off-duty-other", "{player}", target.getName());
                else lang().send(sender, on ? "already-on-duty" : "already-off-duty");
                break;
            case NO_PERMISSION:
                lang().send(sender, "no-permission");
                break;
            case ON_COOLDOWN: {
                int secs = on ? plugin.getCooldownManager().remainingOn(target) : plugin.getCooldownManager().remainingOff(target);
                lang().send(sender, "cooldown-active", "{time}", String.valueOf(Math.max(1, secs)));
                break;
            }
            case DATA_NOT_READY:
                lang().send(sender, "data-not-ready");
                break;
            case WORLD_BLOCKED:
                lang().send(sender, "duty-world-blocked");
                break;
            case CANCELLED:
                lang().send(sender, "duty-cancelled");
                break;
            case REASON_REQUIRED:
                lang().send(sender, "usage-duty-reason", "{label}", "duty");
                break;
            default:
                lang().send(sender, "error");
                break;
        }
    }

    // ------------------------------------------------------------------ status / playtime

    private void status(CommandSender sender, String[] args) {
        if (noPerm(sender, "advancedduty.status")) return;
        Player target;
        if (args.length > 1) {
            if (noPerm(sender, "advancedduty.status.others")) return;
            target = onlineTarget(sender, args[1]);
            if (target == null) return;
        } else {
            target = playerOnly(sender);
            if (target == null) return;
        }
        boolean on = plugin.getDutyManager().isOnDuty(target);
        lang().send(sender, "status-line",
                "{player}", target.getName(),
                "{status}", lang().getPlaceholder(on ? "status_color.on_duty" : "status_color.off_duty"),
                "{time}", PlaytimeFormatter.format(plugin.getPlaytime().getTotal(target.getUniqueId())));
        if (on) {
            DutySession s = plugin.getDutyManager().getSession(target.getUniqueId());
            lang().send(sender, "status-session",
                    "{time}", PlaytimeFormatter.format(plugin.getPlaytime().sessionMs(target.getUniqueId())),
                    "{reason}", s == null || s.getReason() == null || s.getReason().isEmpty() ? lang().gui("no-reason") : s.getReason(),
                    "{afk}", lang().gui(plugin.getAfk().isAfk(target.getUniqueId()) ? "yes" : "no"));
        }
    }

    private void playtime(CommandSender sender, String[] args) {
        if (noPerm(sender, "advancedduty.playtime")) return;
        PlayerLookup.Result who;
        if (args.length > 1) {
            if (noPerm(sender, "advancedduty.playtime.others")) return;
            who = PlayerLookup.find(plugin, args[1]);
            if (who == null) {
                lang().send(sender, "unknown-player");
                return;
            }
        } else {
            Player self = playerOnly(sender);
            if (self == null) return;
            who = PlayerLookup.find(plugin, self.getName());
            if (who == null) return;
        }
        final PlayerLookup.Result target = who;
        final boolean self = sender instanceof Player && ((Player) sender).getUniqueId().equals(target.uuid);
        plugin.getPlaytime().fetch(target.uuid).whenComplete((stats, ex) -> runSync(() -> {
            if (ex != null) {
                lang().send(sender, "error");
                return;
            }
            if (stats == null && !plugin.getPlaytime().hasSession(target.uuid)) {
                lang().send(sender, "playtime-never", "{player}", target.name);
                return;
            }
            PlaytimeManager pm = plugin.getPlaytime();
            boolean cached = pm.cached(target.uuid) != null;
            long all = cached ? pm.get(target.uuid, Period.ALL) : stats.getTotal();
            long today = cached ? pm.get(target.uuid, Period.TODAY) : pm.rangeSum(stats, Period.TODAY);
            long week = cached ? pm.get(target.uuid, Period.WEEK) : pm.rangeSum(stats, Period.WEEK);
            long month = cached ? pm.get(target.uuid, Period.MONTH) : pm.rangeSum(stats, Period.MONTH);
            int sessions = stats == null ? 0 : stats.getSessions();
            long longest = stats == null ? 0 : stats.getLongest();
            int rank = pm.rank(target.uuid, Period.ALL);
            lang().sendList(sender, "playtime-card",
                    "{player}", target.name,
                    "{total}", PlaytimeFormatter.format(all),
                    "{today}", PlaytimeFormatter.format(today),
                    "{week}", PlaytimeFormatter.format(week),
                    "{month}", PlaytimeFormatter.format(month),
                    "{sessions}", String.valueOf(sessions),
                    "{longest}", PlaytimeFormatter.format(longest),
                    "{rank}", rank > 0 ? "#" + rank : "-",
                    "{time}", PlaytimeFormatter.format(all),
                    "{self}", String.valueOf(self));
        }));
    }

    private void top(CommandSender sender, String[] args) {
        if (noPerm(sender, "advancedduty.playtime.top")) return;
        Period period = Period.ALL;
        int page = 1;
        for (int i = 1; i < args.length; i++) {
            Period p = Period.parse(args[i], null);
            if (p != null) period = p;
            else {
                try {
                    page = Math.max(1, Integer.parseInt(args[i]));
                } catch (NumberFormatException ignored) {
                    // not a page number
                }
            }
        }
        List<PlaytimeManager.Row> rows = plugin.getPlaytime().top(period);
        if (rows.isEmpty()) {
            lang().send(sender, "top-empty");
            return;
        }
        int perPage = Math.max(1, plugin.getConfig().getInt("playtime.top-per-page", 10));
        int max = (int) Math.ceil(rows.size() / (double) perPage);
        page = Math.min(page, max);
        lang().sendRaw(sender, "top-header", "{period}", periodName(period));
        for (int i = (page - 1) * perPage; i < Math.min(rows.size(), page * perPage); i++) {
            PlaytimeManager.Row r = rows.get(i);
            lang().sendRaw(sender, "top-entry", "{rank}", String.valueOf(i + 1), "{player}", r.getName(),
                    "{time}", PlaytimeFormatter.format(r.getMs()));
        }
        lang().sendRaw(sender, "top-footer", "{page}", String.valueOf(page), "{max}", String.valueOf(max));
    }

    private void reset(CommandSender sender, String[] args, String label) {
        if (noPerm(sender, "advancedduty.playtime.reset")) return;
        if (args.length < 2) {
            lang().send(sender, "usage-reset", "{label}", label);
            return;
        }
        PlayerLookup.Result target = PlayerLookup.find(plugin, args[1]);
        if (target == null) {
            lang().send(sender, "unknown-player");
            return;
        }
        if (plugin.getConfig().getBoolean("playtime.reset-requires-confirm", true)) {
            UUID key = sender instanceof Player ? ((Player) sender).getUniqueId() : new UUID(0, 0);
            String[] pending = resetConfirm.get(key);
            boolean confirmed = args.length > 2 && args[2].equalsIgnoreCase("confirm") && pending != null
                    && pending[0].equals(target.uuid.toString()) && System.currentTimeMillis() - Long.parseLong(pending[1]) < 30_000L;
            if (!confirmed) {
                resetConfirm.put(key, new String[]{target.uuid.toString(), String.valueOf(System.currentTimeMillis())});
                lang().send(sender, "reset-confirm", "{player}", target.name, "{label}", label);
                return;
            }
            resetConfirm.remove(key);
        }
        plugin.getPlaytime().reset(target.uuid);
        lang().send(sender, "playtime-reset", "{player}", target.name);
    }

    // ------------------------------------------------------------------ history

    private void log(CommandSender sender, String[] args) {
        if (noPerm(sender, "advancedduty.log")) return;
        PlayerLookup.Result who = null;
        int page = 1;
        for (int i = 1; i < args.length; i++) {
            try {
                page = Math.max(1, Integer.parseInt(args[i]));
            } catch (NumberFormatException ex) {
                who = PlayerLookup.find(plugin, args[i]);
                if (who == null) {
                    lang().send(sender, "unknown-player");
                    return;
                }
            }
        }
        if (who == null) {
            Player self = playerOnly(sender);
            if (self == null) return;
            who = PlayerLookup.find(plugin, self.getName());
        } else if (!(sender instanceof Player && ((Player) sender).getUniqueId().equals(who.uuid))
                && noPerm(sender, "advancedduty.log.others")) {
            return;
        }
        final PlayerLookup.Result target = who;
        final int perPage = Math.max(1, plugin.getConfig().getInt("history.per-page", 8));
        final int pageNo = page;
        final String format = plugin.getConfig().getString("history.date-format", "yyyy-MM-dd HH:mm");

        plugin.getHistory().count(target.uuid).thenCompose(total -> plugin.getHistory().page(target.uuid, perPage, (pageNo - 1) * perPage)
                .thenApply(list -> new Object[]{total, list})).whenComplete((res, ex) -> runSync(() -> {
            if (ex != null) {
                lang().send(sender, "error");
                return;
            }
            @SuppressWarnings("unchecked")
            List<SessionRecord> list = (List<SessionRecord>) res[1];
            int total = (Integer) res[0];
            boolean active = plugin.getDutyManager().isOnDuty(target.uuid);
            if (list.isEmpty() && !active) {
                lang().sendRaw(sender, "log-empty", "{player}", target.name);
                return;
            }
            lang().sendRaw(sender, "log-header", "{player}", target.name);
            if (active && pageNo == 1) {
                DutySession s = plugin.getDutyManager().getSession(target.uuid);
                lang().sendRaw(sender, "log-entry", "{index}", "*", "{start}",
                        TimeUtil.formatDate(plugin.getPlaytime().sessionStart(target.uuid), format, zone()),
                        "{end}", lang().raw("log-active"),
                        "{duration}", PlaytimeFormatter.format(plugin.getPlaytime().sessionMs(target.uuid)),
                        "{reason}", s == null || s.getReason() == null ? "" : s.getReason());
            }
            int index = total - (pageNo - 1) * perPage;
            for (SessionRecord r : list) {
                lang().sendRaw(sender, "log-entry", "{index}", String.valueOf(index--),
                        "{start}", TimeUtil.formatDate(r.getStartMs(), format, zone()),
                        "{end}", TimeUtil.formatDate(r.getEndMs(), format, zone()),
                        "{duration}", PlaytimeFormatter.format(r.getActiveMs()),
                        "{reason}", r.getReason() == null ? "" : r.getReason());
            }
            int max = Math.max(1, (int) Math.ceil(total / (double) perPage));
            lang().sendRaw(sender, "top-footer", "{page}", String.valueOf(Math.min(pageNo, max)), "{max}", String.valueOf(max));
        }));
    }

    // ------------------------------------------------------------------ lists / goals / quota

    private void staffList(CommandSender sender) {
        if (noPerm(sender, "advancedduty.stafflist")) return;
        boolean seeVanished = sender.hasPermission("advancedduty.vanish.see");
        List<StaffEntry> staff = plugin.getStaffList().snapshot(seeVanished);
        if (staff.isEmpty()) {
            lang().send(sender, "stafflist-empty");
            return;
        }
        int on = 0;
        for (StaffEntry e : staff) if (e.isOnDuty()) on++;
        lang().sendRaw(sender, "stafflist-header", "{count}", String.valueOf(on), "{total}", String.valueOf(staff.size()));
        for (StaffEntry e : staff) {
            String time = e.isOnDuty() ? PlaytimeFormatter.format(Math.max(0, System.currentTimeMillis() - e.getSessionMs())) : "-";
            lang().sendRaw(sender, e.isOnDuty() ? "stafflist-on" : "stafflist-off",
                    "{player}", e.getName(), "{time}", time,
                    "{server}", e.getServer() == null ? "" : e.getServer(),
                    "{afk}", e.isAfk() ? lang().raw("stafflist-afk") : "",
                    "{vanished}", e.isVanished() ? lang().raw("stafflist-vanished") : "");
        }
    }

    private void goals(CommandSender sender) {
        if (noPerm(sender, "advancedduty.goals")) return;
        Player p = playerOnly(sender);
        if (p == null) return;
        List<String[]> goals = plugin.getMilestones().describeGoals(p);
        if (goals.isEmpty()) {
            lang().send(sender, "goals-none");
            return;
        }
        lang().sendRaw(sender, "goals-header");
        for (String[] g : goals) {
            lang().sendRaw(sender, g[4].equals("true") ? "goals-entry-done" : "goals-entry",
                    "{goal}", g[0], "{have}", g[1], "{required}", g[2], "{percent}", g[3],
                    "{period}", periodName(Period.parse(g[5], Period.WEEK)));
        }
    }

    private void quota(CommandSender sender, String[] args) {
        if (noPerm(sender, "advancedduty.quota")) return;
        if (!plugin.getMilestones().isQuotaEnabled()) {
            lang().send(sender, "quota-disabled");
            return;
        }
        if (args.length > 1 && args[1].equalsIgnoreCase("report")) {
            if (noPerm(sender, "advancedduty.quota.report")) return;
            quotaReport(sender);
            return;
        }
        Player target;
        if (args.length > 1) {
            if (noPerm(sender, "advancedduty.quota.others")) return;
            target = onlineTarget(sender, args[1]);
        } else {
            target = playerOnly(sender);
        }
        if (target == null) return;
        plugin.getMilestones().sendProgress(target, sender);
    }

    private void quotaReport(CommandSender sender) {
        Period period = plugin.getMilestones().quotaPeriod();
        long required = plugin.getMilestones().requiredDefault();
        List<String> lines = new ArrayList<>();
        java.util.Set<UUID> seen = new java.util.HashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.hasPermission("advancedduty.use")) continue;
            long req = plugin.getMilestones().requiredFor(p);
            if (req <= 0) continue;
            seen.add(p.getUniqueId());
            long have = plugin.getPlaytime().get(p.getUniqueId(), period);
            if (have < req) lines.add(lang().raw("quota-report-entry", "{player}", p.getName(),
                    "{time}", PlaytimeFormatter.format(have), "{required}", PlaytimeFormatter.format(req)));
        }
        for (PlaytimeManager.Row r : plugin.getPlaytime().top(period)) {
            if (seen.contains(r.getUuid()) || required <= 0) continue;
            if (r.getMs() < required) lines.add(lang().raw("quota-report-entry", "{player}", r.getName(),
                    "{time}", PlaytimeFormatter.format(r.getMs()), "{required}", PlaytimeFormatter.format(required)));
        }
        if (lines.isEmpty()) {
            lang().send(sender, "quota-report-all-met");
            return;
        }
        lang().sendRaw(sender, "quota-report-header", "{required}", PlaytimeFormatter.format(required), "{period}", periodName(period));
        for (String l : lines) sender.sendMessage(l);
    }

    // ------------------------------------------------------------------ staff tools

    private void vanish(CommandSender sender) {
        Player p = playerOnly(sender);
        if (p == null) return;
        plugin.getVanish().toggleBy(p);
    }

    private void freeze(CommandSender sender, String[] args, boolean freeze) {
        if (noPerm(sender, "advancedduty.freeze")) return;
        if (args.length < 2) {
            lang().send(sender, "usage-freeze", "{label}", freeze ? "freeze" : "unfreeze");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || !visibleTo(sender, target)) {
            if (!freeze) {
                PlayerLookup.Result r = PlayerLookup.find(plugin, args[1]);
                if (r != null && plugin.getFreeze().unfreezeOffline(r.uuid)) {
                    lang().send(sender, "unfreeze-done", "{player}", r.name);
                    return;
                }
            }
            lang().send(sender, "unknown-player");
            return;
        }
        if (freeze) {
            if (plugin.getFreeze().isFrozen(target.getUniqueId())) plugin.getFreeze().unfreeze(sender, target);
            else plugin.getFreeze().freeze(sender, target);
        } else {
            plugin.getFreeze().unfreeze(sender, target);
        }
    }

    private void inspect(CommandSender sender, String[] args, boolean ender) {
        if (noPerm(sender, "advancedduty.inspect")) return;
        Player p = playerOnly(sender);
        if (p == null) return;
        if (args.length < 2) {
            lang().send(sender, "usage-inspect", "{label}", ender ? "echest" : "inspect");
            return;
        }
        Player target = onlineTarget(sender, args[1]);
        if (target == null) return;
        if (ender) plugin.getInspect().openEnderChest(p, target);
        else plugin.getInspect().open(p, target);
    }

    private void gui(CommandSender sender) {
        if (noPerm(sender, "advancedduty.gui")) return;
        Player p = playerOnly(sender);
        if (p != null) plugin.getStaffGui().openMenu(p);
    }

    private void scoreboard(CommandSender sender) {
        if (noPerm(sender, "advancedduty.scoreboard")) return;
        Player p = playerOnly(sender);
        if (p != null) plugin.getDisplays().toggleScoreboard(p);
    }

    private void staffChat(CommandSender sender, String[] args) {
        if (noPerm(sender, "advancedduty.staffchat")) return;
        if (args.length < 2) {
            Player p = playerOnly(sender);
            if (p == null) return;
            boolean on = plugin.getStaffChat().toggleMode(p);
            lang().send(p, on ? "staffchat-mode-on" : "staffchat-mode-off");
            return;
        }
        plugin.getStaffChat().send(sender, join(args, 1));
    }

    // ------------------------------------------------------------------ admin

    private boolean reload(CommandSender sender) {
        if (noPerm(sender, "advancedduty.reload")) return true;
        plugin.reloadPlugin();
        lang().send(sender, "config-reloaded");
        return true;
    }

    private boolean version(CommandSender sender) {
        lang().send(sender, "version", "{version}", plugin.getDescription().getVersion());
        return true;
    }

    private boolean info(CommandSender sender) {
        if (noPerm(sender, "advancedduty.reload")) return true;
        lang().sendList(sender, "info",
                "{version}", plugin.getDescription().getVersion(),
                "{storage}", plugin.getData().activeType().name(),
                "{language}", lang().getCurrentLanguage(),
                "{luckperms}", String.valueOf(plugin.getLuckPerms().isAvailable()),
                "{papi}", String.valueOf(me.hesamai.advancedduty.util.PapiUtil.isAvailable()),
                "{onduty}", String.valueOf(plugin.getDutyManager().getOnDutyPlayers().size()),
                "{frozen}", String.valueOf(plugin.getFreeze().count()),
                "{vanished}", String.valueOf(plugin.getVanish().getVanished().size()),
                "{network}", String.valueOf(plugin.getNetwork().isEnabled()),
                "{discord}", String.valueOf(plugin.getConfig().getBoolean("discord.enabled", false)));
        return true;
    }

    private boolean languages(CommandSender sender) {
        Player p = playerOnly(sender);
        if (p == null) return true;
        if (noPerm(sender, "advancedduty.languages")) return true;
        LanguageGUI.open(plugin, p, 1);
        return true;
    }

    private boolean update(CommandSender sender) {
        if (noPerm(sender, "advancedduty.update")) return true;
        lang().send(sender, "update-checking");
        plugin.getUpdates().check(result -> runSync(() -> {
            if (result.failed) {
                lang().send(sender, "update-failed");
            } else if (!result.updateAvailable) {
                lang().send(sender, "update-latest");
            } else {
                lang().send(sender, "update-found", "{version}", result.latest);
                if (result.downloadUrl != null) {
                    plugin.getUpdates().download(result.downloadUrl, result.latest, ok ->
                            runSync(() -> lang().send(sender, ok ? "update-downloaded" : "update-download-failed")));
                    lang().send(sender, "update-downloading");
                } else {
                    lang().send(sender, "update-no-jar");
                }
            }
        }));
        return true;
    }

    private boolean help(CommandSender sender, String label) {
        lang().sendRaw(sender, "help-header");
        String[][] lines = {
                {"advancedduty.use", "help-on"}, {"advancedduty.use", "help-off"}, {"advancedduty.use", "help-toggle"},
                {"advancedduty.status", "help-status"}, {"advancedduty.playtime", "help-playtime"},
                {"advancedduty.playtime.top", "help-playtimetop"}, {"advancedduty.log", "help-log"},
                {"advancedduty.stafflist", "help-list"}, {"advancedduty.goals", "help-goals"},
                {"advancedduty.quota", "help-quota"}, {"advancedduty.vanish", "help-vanish"},
                {"advancedduty.freeze", "help-freeze"}, {"advancedduty.inspect", "help-inspect"},
                {"advancedduty.gui", "help-gui"}, {"advancedduty.scoreboard", "help-scoreboard"},
                {"advancedduty.staffchat", "help-staffchat"}, {"advancedduty.playtime.reset", "help-reset"},
                {"advancedduty.reload", "help-reload"}, {"advancedduty.languages", "help-languages"},
                {"advancedduty.update", "help-update"}, {"", "help-version"}
        };
        for (String[] l : lines) {
            if (l[0].isEmpty() || sender.hasPermission(l[0])) lang().sendRaw(sender, l[1], "{label}", label);
        }
        return true;
    }

    // ------------------------------------------------------------------ tab completion

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            for (String[] s : SUBS) if (s[1].isEmpty() || sender.hasPermission(s[1])) subs.add(s[0]);
            return filter(subs, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            switch (sub) {
                case "on": case "off": case "toggle":
                    List<String> opts = new ArrayList<>();
                    if (sender.hasPermission("advancedduty.use.others")) opts.addAll(visibleNames(sender));
                    if (sender.hasPermission("advancedduty.silent")) opts.add("-s");
                    if (sub.equals("on") || sub.equals("toggle")) opts.addAll(plugin.getConfig().getStringList("duty.reasons.presets"));
                    return filter(opts, args[1]);
                case "status": case "playtime": case "reset": case "log": case "quota": case "freeze": case "unfreeze":
                case "inspect": case "echest":
                    if (sub.equals("quota") && sender.hasPermission("advancedduty.quota.report")) {
                        List<String> q = visibleNames(sender);
                        q.add("report");
                        return filter(q, args[1]);
                    }
                    return filter(visibleNames(sender), args[1]);
                case "playtimetop":
                    return filter(Arrays.asList("all", "today", "week", "month"), args[1]);
                default:
                    return Collections.emptyList();
            }
        }
        if (args.length == 3) {
            if (sub.equals("reset")) return filter(Collections.singletonList("confirm"), args[2]);
            if (sub.equals("playtimetop")) return filter(Arrays.asList("1", "2", "3"), args[2]);
            if ((sub.equals("on") || sub.equals("toggle")) && sender.hasPermission("advancedduty.use.others")) {
                return filter(plugin.getConfig().getStringList("duty.reasons.presets"), args[2]);
            }
        }
        return Collections.emptyList();
    }

    private List<String> visibleNames(CommandSender sender) {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) if (visibleTo(sender, p)) names.add(p.getName());
        return names;
    }

    private static List<String> filter(List<String> list, String prefix) {
        List<String> out = new ArrayList<>();
        String p = prefix.toLowerCase(Locale.ROOT);
        for (String s : list) if (s.toLowerCase(Locale.ROOT).startsWith(p)) out.add(s);
        Collections.sort(out);
        return out;
    }
}
