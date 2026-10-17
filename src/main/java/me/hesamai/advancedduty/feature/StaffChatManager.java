package me.hesamai.advancedduty.feature;

import me.hesamai.advancedduty.Main;
import me.hesamai.advancedduty.util.MessageUtil;
import me.hesamai.advancedduty.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Staff chat: {@code /sc <message>}, a toggle mode ({@code /sc} alone) and an optional chat prefix such as
 * {@code #hello}. Messages are formatted with the plain colour code parser - player input can never
 * become a formatting tag or a clickable component.
 */
public class StaffChatManager implements Listener {

    private final Main plugin;
    private final Set<UUID> toggled = ConcurrentHashMap.newKeySet();

    public StaffChatManager(Main plugin) {
        this.plugin = plugin;
    }

    public boolean isToggled(UUID id) {
        return toggled.contains(id);
    }

    /** Switches the sticky staff-chat mode; returns the new state. */
    public boolean toggleMode(Player p) {
        if (toggled.remove(p.getUniqueId())) return false;
        toggled.add(p.getUniqueId());
        return true;
    }

    public void setMode(Player p, boolean on) {
        if (on) toggled.add(p.getUniqueId());
        else toggled.remove(p.getUniqueId());
    }

    /** @return false when the sender is not allowed (a message was sent to them) */
    public boolean send(CommandSender sender, String rawMessage) {
        if (!sender.hasPermission("advancedduty.staffchat")) {
            plugin.getLanguageManager().send(sender, "no-permission");
            return false;
        }
        if (sender instanceof Player) {
            Player p = (Player) sender;
            if (plugin.getConfig().getBoolean("staffchat.require-on-duty", true) && !plugin.getDutyManager().isOnDuty(p)) {
                plugin.getLanguageManager().send(p, "staffchat-not-on-duty");
                return false;
            }
        }
        String text = rawMessage.trim();
        if (text.isEmpty()) return true;
        if (text.length() > plugin.getConfig().getInt("staffchat.max-length", 256)) {
            text = text.substring(0, plugin.getConfig().getInt("staffchat.max-length", 256));
        }

        String senderName = sender instanceof Player ? sender.getName() : "Console";
        if (!sender.hasPermission("advancedduty.staffchat.color")) text = MessageUtil.stripCodes(text);

        String message = highlightMentions(text);
        String server = plugin.getNetwork().isEnabled() ? plugin.getNetwork().getServerName() : "";
        String line = format(sender, senderName, message, server);
        deliver(line, true);

        if (plugin.getConfig().getBoolean("staffchat.log-to-console", true)) {
            plugin.getLogger().info("[StaffChat] " + senderName + ": " + MessageUtil.stripCodes(text));
        }
        plugin.getDiscord().sendStaffChat(senderName, text);
        plugin.getNetwork().sendChat(senderName, text);
        return true;
    }

    private String format(CommandSender sender, String name, String message, String server) {
        String prefix = "";
        String suffix = "";
        String world = "";
        String display = name;
        if (sender instanceof Player) {
            Player p = (Player) sender;
            prefix = plugin.getLuckPerms().prefixOf(p.getUniqueId());
            suffix = plugin.getLuckPerms().suffixOf(p.getUniqueId());
            world = p.getWorld().getName();
            display = p.getDisplayName();
        }
        String fmt = plugin.getConfig().getString("staffchat.format", "&8[&bSTAFF&8] &7{player}&8: &f{message}");
        // the message is inserted AFTER colouring, so it cannot inject codes through the format
        String coloured = MessageUtil.color(fmt
                .replace("{player}", name).replace("{prefix}", prefix).replace("{suffix}", suffix)
                .replace("{world}", world).replace("{displayname}", display).replace("{server}", server));
        return coloured.replace("{message}", MessageUtil.color(message));
    }

    /** Shows a message that came from another server. */
    public void receiveRemote(String server, String sender, String message) {
        String fmt = plugin.getConfig().getString("staffchat.format-remote", "&8[&bSTAFF&8|&d{server}&8] &7{player}&8: &f{message}");
        String line = MessageUtil.color(fmt.replace("{player}", sender).replace("{server}", server))
                .replace("{message}", MessageUtil.color(message));
        deliver(line, false);
    }

    private void deliver(String line, boolean local) {
        boolean sound = plugin.getConfig().getBoolean("staffchat.sound.enabled", false);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!(p.hasPermission("advancedduty.staffchat.receive") || plugin.getDutyManager().isOnDuty(p))) continue;
            p.sendMessage(line);
            if (sound) {
                SoundUtil.play(p, plugin.getConfig().getString("staffchat.sound.name", "BLOCK_NOTE_BLOCK_PLING"),
                        (float) plugin.getConfig().getDouble("staffchat.sound.volume", 1.0),
                        (float) plugin.getConfig().getDouble("staffchat.sound.pitch", 1.2));
            }
        }
    }

    private String highlightMentions(String message) {
        if (!plugin.getConfig().getBoolean("staffchat.mentions.enabled", true)) return message;
        String color = plugin.getConfig().getString("staffchat.mentions.color", "&a");
        String reset = plugin.getConfig().getString("staffchat.mentions.reset-color", "&f");
        boolean needAt = plugin.getConfig().getBoolean("staffchat.mentions.require-at", false);

        String out = message;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!(p.hasPermission("advancedduty.staffchat.receive") || plugin.getDutyManager().isOnDuty(p))) continue;
            String name = p.getName();
            Pattern pat = Pattern.compile("(?i)(?<![A-Za-z0-9_])" + (needAt ? "@" : "@?") + Pattern.quote(name) + "(?![A-Za-z0-9_])");
            Matcher m = pat.matcher(out);
            if (m.find()) {
                out = pat.matcher(out).replaceAll(Matcher.quoteReplacement(color + "@" + name + reset));
                if (plugin.getConfig().getBoolean("staffchat.mentions.sound.enabled", true)) {
                    SoundUtil.play(p, plugin.getConfig().getString("staffchat.mentions.sound.name", "ENTITY_EXPERIENCE_ORB_PICKUP"),
                            (float) plugin.getConfig().getDouble("staffchat.mentions.sound.volume", 1.0),
                            (float) plugin.getConfig().getDouble("staffchat.mentions.sound.pitch", 2.0));
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ chat interception

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (!p.hasPermission("advancedduty.staffchat")) return;

        String message = e.getMessage();
        String prefix = plugin.getConfig().getString("staffchat.chat-prefix", "");
        boolean viaPrefix = !prefix.isEmpty() && message.startsWith(prefix) && message.length() > prefix.length();
        boolean viaToggle = toggled.contains(p.getUniqueId());
        if (!viaPrefix && !viaToggle) return;

        e.setCancelled(true);
        final String text = viaPrefix ? message.substring(prefix.length()) : message;
        Bukkit.getScheduler().runTask(plugin, () -> send(p, text));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (!plugin.getConfig().getBoolean("staffchat.toggle-persist", false)) toggled.remove(e.getPlayer().getUniqueId());
    }
}
