package me.hesamai.advancedduty.command;

import me.hesamai.advancedduty.Main;
import me.hesamai.advancedduty.duty.DutyManager;
import me.hesamai.advancedduty.duty.playtime.PlaytimeFormatter;
import me.hesamai.advancedduty.duty.playtime.PlaytimeManager;
import me.hesamai.advancedduty.lang.LanguageManager;
import me.hesamai.advancedduty.lang.gui.LanguageGUI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

public class DutyCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final DutyManager dutyManager;
    private final LanguageManager lang;

    public DutyCommand(Main plugin, DutyManager dutyManager, LanguageManager lang) {
        this.plugin = plugin;
        this.dutyManager = dutyManager;
        this.lang = lang;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase() : "";

        switch (sub) {
            case "reload":
                return handleReload(sender);
            case "version":
                return handleVersion(sender);
            case "help":
                return handleHelp(sender, label);
            case "playtimetop":
                handleTop(sender, args);
                return true;
            case "playtime":
                handlePlaytime(sender, args);
                return true;
            case "status":
                handleStatus(sender, args);
                return true;
            case "reset":
                handleReset(sender, args);
                return true;
            case "languages":
                return handleLanguages(sender);
            case "update":
                return handleUpdate(sender);
            default:
                return handleDutyToggle(sender, label, args, sub);
        }
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("advancedduty.reload")) {
            noPerms(sender);
            return true;
        }
        plugin.reloadPlugin();
        sender.sendMessage(lang.getMessage("config-reloaded"));
        return true;
    }

    private boolean handleVersion(CommandSender sender) {
        sender.sendMessage(lang.getMessage("version", "{version}", plugin.getDescription().getVersion()));
        return true;
    }

    private boolean handleDutyToggle(CommandSender sender, String label, String[] args, String sub) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(lang.getMessageWithoutPrefix("only-player"));
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("advancedduty.use")) {
            noPerms(player);
            return true;
        }

        boolean requireReason = plugin.getConfig().getBoolean("duty.require-reason", false);
        DutyTargetContext ctx = resolveDutyTarget(player, args);

        Player target = ctx.target;
        String reason = ctx.reason;

        boolean targetingOther = !target.getUniqueId().equals(player.getUniqueId());

        if (targetingOther && !player.hasPermission("advancedduty.use.others")) {
            noPerms(player);
            return true;
        }

        if (targetingOther && !target.hasPermission("advancedduty.use")) {
            player.sendMessage(lang.getMessage("target-no-duty-access", "{player}", target.getName()));
            return true;
        }

        switch (sub) {
            case "on":
                if (dutyManager.isOnDuty(target)) {
                    if (targetingOther) {
                        player.sendMessage(lang.getMessage("already-on-duty-other", "{player}", target.getName()));
                    } else {
                        player.sendMessage(lang.getMessage("already-on-duty"));
                    }
                    return true;
                }

                if (requireReason && (reason == null || reason.trim().isEmpty())) {
                    player.sendMessage(lang.getMessage("usage-duty-reason", "{label}", label));
                    return true;
                }

                if (dutyManager.enableDuty(target, reason)) {
                    sendDutyResultMessage(player, target, true, targetingOther);
                }
                return true;

            case "toggle":
            case "":
                boolean goingOn = !dutyManager.isOnDuty(target);

                if (goingOn && requireReason && (reason == null || reason.trim().isEmpty())) {
                    player.sendMessage(lang.getMessage("usage-duty-reason", "{label}", label));
                    return true;
                }

                if (dutyManager.toggleDuty(target, reason)) {
                    sendDutyResultMessage(player, target, dutyManager.isOnDuty(target), targetingOther);
                }
                return true;

            case "off":
                if (!dutyManager.isOnDuty(target)) {
                    if (targetingOther) {
                        player.sendMessage(lang.getMessage("already-off-duty-other", "{player}", target.getName()));
                    } else {
                        player.sendMessage(lang.getMessage("already-off-duty"));
                    }
                    return true;
                }

                if (dutyManager.disableDuty(target)) {
                    sendDutyResultMessage(player, target, false, targetingOther);
                }
                return true;

            default:
                player.sendMessage(lang.getMessage("usage-duty", "{label}", label));
                return true;
        }
    }

    private void handleStatus(CommandSender sender, String[] args) {
        if (!sender.hasPermission("advancedduty.status")) {
            noPerms(sender);
            return;
        }

        Player target = resolveOnlinePlayer(sender, args, 1, "advancedduty.status.others");
        if (target == null) return;

        boolean onDuty = dutyManager.isOnDuty(target);
        Map<String, String> ph = new HashMap<String, String>();
        ph.put("{player}", target.getName());
        ph.put("{status}", lang.getPlaceholder(onDuty ? "status_color.on_duty" : "status_color.off_duty"));
        ph.put("{time}", PlaytimeFormatter.format(dutyManager.getPlaytimeManager().getTotalPlaytime(target.getUniqueId())));
        sender.sendMessage(lang.getMessage("status-line", ph));
    }

    private void handlePlaytime(CommandSender sender, String[] args) {
        if (!sender.hasPermission("advancedduty.playtime")) {
            noPerms(sender);
            return;
        }

        PlaytimeManager pm = dutyManager.getPlaytimeManager();

        if (args.length < 2) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(lang.getMessageWithoutPrefix("only-player"));
                return;
            }
            Player p = (Player) sender;
            sender.sendMessage(lang.getMessage("playtime-self", "{time}", PlaytimeFormatter.format(pm.getTotalPlaytime(p.getUniqueId()))));
            return;
        }

        if (!sender.hasPermission("advancedduty.playtime.others")) {
            noPerms(sender);
            return;
        }

        OfflinePlayer target = resolveOfflinePlayer(sender, args[1]);
        if (target == null) return;

        String name = target.getName() != null ? target.getName() : args[1];
        long ms = pm.getTotalPlaytime(target.getUniqueId());
        if (ms == 0) {
            sender.sendMessage(lang.getMessage("playtime-never", "{player}", name));
            return;
        }

        Map<String, String> ph = new HashMap<String, String>();
        ph.put("{player}", name);
        ph.put("{time}", PlaytimeFormatter.format(ms));
        sender.sendMessage(lang.getMessage("playtime-other", ph));
    }

    private void handleTop(CommandSender sender, String[] args) {
        if (!sender.hasPermission("advancedduty.playtime.top")) {
            noPerms(sender);
            return;
        }

        int page = parsePage(args, 1);
        int perPage = plugin.getConfig().getInt("playtime.top-per-page", 10);
        PlaytimeManager pm = dutyManager.getPlaytimeManager();

        List<Map.Entry<UUID, Long>> sorted = new ArrayList<Map.Entry<UUID, Long>>(pm.getAllStored().entrySet());
        sorted.sort(new Comparator<Map.Entry<UUID, Long>>() {
            @Override
            public int compare(Map.Entry<UUID, Long> a, Map.Entry<UUID, Long> b) {
                return Long.compare(b.getValue(), a.getValue());
            }
        });

        if (sorted.isEmpty()) {
            sender.sendMessage(lang.getMessage("top-empty"));
            return;
        }

        int maxPage = (int) Math.ceil((double) sorted.size() / perPage);
        page = Math.min(page, maxPage);
        int start = (page - 1) * perPage;
        int end = Math.min(start + perPage, sorted.size());

        sender.sendMessage(lang.getMessage("top-header"));
        for (int i = start; i < end; i++) {
            Map.Entry<UUID, Long> e = sorted.get(i);
            @SuppressWarnings("deprecation")
            String name = Bukkit.getOfflinePlayer(e.getKey()).getName();
            if (name == null) name = e.getKey().toString().substring(0, 8);

            Map<String, String> ph = new HashMap<String, String>();
            ph.put("{rank}", String.valueOf(i + 1));
            ph.put("{player}", name);
            ph.put("{time}", PlaytimeFormatter.format(e.getValue()));
            sender.sendMessage(lang.getMessage("top-entry", ph));
        }
        sendFooter(sender, page, maxPage);
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("advancedduty.playtime.reset")) {
            noPerms(sender);
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(lang.getMessage("usage-reset", "{label}", "duty"));
            return;
        }

        OfflinePlayer target = resolveOfflinePlayer(sender, args[1]);
        if (target == null) return;

        dutyManager.getPlaytimeManager().resetPlaytime(target.getUniqueId());
        String name = target.getName() != null ? target.getName() : args[1];
        sender.sendMessage(lang.getMessage("playtime-reset", "{player}", name));
    }

    private boolean handleLanguages(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(lang.getMessageWithoutPrefix("only-player"));
            return true;
        }

        if (!sender.hasPermission("advancedduty.languages")) {
            noPerms(sender);
            return true;
        }

        me.hesamai.advancedduty.lang.gui.LanguageGUI.open(plugin, (Player) sender, 1);
        return true;
    }

    private boolean handleUpdate(CommandSender sender){

        if(!sender.hasPermission("advancedduty.update")){
            noPerms(sender);
            return true;
        }

        sender.sendMessage(lang.getMessage("update-checking"));

        plugin.getUpdateChecker().check((latest,current,update,url)->{

            if(update){

                sender.sendMessage(lang.getMessage("update-found","{version}",latest));

                plugin.getUpdateDownloader().download(url,latest);

                sender.sendMessage(lang.getMessage("update-downloading"));

            }else{

                sender.sendMessage(lang.getMessage("update-latest"));

            }

        });

        return true;
    }

    private boolean handleHelp(CommandSender sender, String label) {
        sender.sendMessage(lang.getMessage("help-header"));

        if (sender.hasPermission("advancedduty.use")) {
            sender.sendMessage(lang.getMessage("help-on", "{label}", label));
            sender.sendMessage(lang.getMessage("help-off", "{label}", label));
            sender.sendMessage(lang.getMessage("help-toggle", "{label}", label));
        }
        if (sender.hasPermission("advancedduty.status")) {
            sender.sendMessage(lang.getMessage("help-status", "{label}", label));
        }
        if (sender.hasPermission("advancedduty.playtime")) {
            sender.sendMessage(lang.getMessage("help-playtime", "{label}", label));
        }
        if (sender.hasPermission("advancedduty.playtime.top")) {
            sender.sendMessage(lang.getMessage("help-playtimetop", "{label}", label));
        }
        if (sender.hasPermission("advancedduty.playtime.reset")) {
            sender.sendMessage(lang.getMessage("help-reset", "{label}", label));
        }
        if (sender.hasPermission("advancedduty.reload")) {
            sender.sendMessage(lang.getMessage("help-reload", "{label}", label));
        }
        if (sender.hasPermission("advancedduty.languages")) {
            sender.sendMessage(lang.getMessage("help-languages", "{label}", label));
        }
        if (sender.hasPermission("advancedduty.update")) {
            sender.sendMessage(lang.getMessage("help-update", "{label}", label));
        }
        sender.sendMessage(lang.getMessage("help-version", "{label}", label));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<String>();
            subs.add("help");
            subs.add("version");

            if (sender.hasPermission("advancedduty.use")) {
                subs.add("on");
                subs.add("off");
                subs.add("toggle");
            }
            if (sender.hasPermission("advancedduty.status")) {
                subs.add("status");
            }
            if (sender.hasPermission("advancedduty.playtime")) {
                subs.add("playtime");
            }
            if (sender.hasPermission("advancedduty.playtime.top")) {
                subs.add("playtimetop");
            }
            if (sender.hasPermission("advancedduty.playtime.reset")) {
                subs.add("reset");
            }
            if (sender.hasPermission("advancedduty.reload")) {
                subs.add("reload");
            }
            if (sender.hasPermission("advancedduty.languages")) {
                subs.add("languages");
            }

            return filter(subs, args[0]);
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();

            boolean needsPlayer =
                    (sub.equals("status") && sender.hasPermission("advancedduty.status.others")) ||
                            (sub.equals("playtime") && sender.hasPermission("advancedduty.playtime.others")) ||
                            (sub.equals("reset") && sender.hasPermission("advancedduty.playtime.reset")) ||
                            ((sub.equals("on") || sub.equals("off") || sub.equals("toggle"))
                                    && sender.hasPermission("advancedduty.use.others"));

            if (needsPlayer) {
                List<String> names = new ArrayList<String>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    names.add(p.getName());
                }
                return filter(names, args[1]);
            }
        }

        return Collections.emptyList();
    }

    private void noPerms(CommandSender s) {
        s.sendMessage(lang.getMessage("no-permission"));
    }

    private void sendFooter(CommandSender sender, int page, int maxPage) {
        Map<String, String> fp = new HashMap<String, String>();
        fp.put("{page}", String.valueOf(page));
        fp.put("{max}", String.valueOf(maxPage));
        sender.sendMessage(lang.getMessage("top-footer", fp));
    }

    private Player resolveOnlinePlayer(CommandSender sender, String[] args, int index, String othersPermission) {
        if (args.length > index) {
            if (!sender.hasPermission(othersPermission)) {
                noPerms(sender);
                return null;
            }

            Player p = Bukkit.getPlayer(args[index]);
            if (p == null) {
                sender.sendMessage(lang.getMessage("unknown-player"));
                return null;
            }
            return p;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(lang.getMessageWithoutPrefix("only-player"));
            return null;
        }
        return (Player) sender;
    }

    @SuppressWarnings("deprecation")
    private OfflinePlayer resolveOfflinePlayer(CommandSender sender, String name) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(name);
        if (!op.hasPlayedBefore() && !op.isOnline()) {
            sender.sendMessage(lang.getMessage("unknown-player"));
            return null;
        }
        return op;
    }

    private String joinArgs(String[] args, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (i > from) sb.append(' ');
            sb.append(args[i]);
        }
        return sb.toString();
    }

    private int parsePage(String[] args, int index) {
        if (index > 0 && index < args.length && isNumeric(args[index])) {
            try {
                return Math.max(1, Integer.parseInt(args[index]));
            } catch (NumberFormatException ignored) {
            }
        }
        return 1;
    }

    private boolean isNumeric(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private List<String> filter(List<String> list, String prefix) {
        List<String> out = new ArrayList<String>();
        for (String s : list) {
            if (s.toLowerCase().startsWith(prefix.toLowerCase())) {
                out.add(s);
            }
        }
        return out;
    }

    private static class DutyTargetContext {
        private final Player target;
        private final String reason;

        private DutyTargetContext(Player target, String reason) {
            this.target = target;
            this.reason = reason;
        }
    }

    private DutyTargetContext resolveDutyTarget(Player sender, String[] args) {
        Player target = sender;
        String reason = null;

        if (args.length < 2) {
            return new DutyTargetContext(target, null);
        }

        if (sender.hasPermission("advancedduty.use.others")) {
            Player possibleTarget = Bukkit.getPlayerExact(args[1]);
            if (possibleTarget != null) {
                target = possibleTarget;
                reason = args.length >= 3 ? joinArgs(args, 2) : null;
                return new DutyTargetContext(target, reason);
            }
        }

        reason = joinArgs(args, 1);
        return new DutyTargetContext(target, reason);
    }

    private void sendDutyResultMessage(Player sender, Player target, boolean on, boolean targetingOther) {
        target.sendMessage(lang.getMessage(on ? "duty-enabled" : "duty-disabled"));

        if (!targetingOther) {
            return;
        }

        Map<String, String> ph = new HashMap<String, String>();
        ph.put("{player}", target.getName());
        sender.sendMessage(lang.getMessage(
                on ? "duty-enabled-other" : "duty-disabled-other",
                ph
        ));
    }
}