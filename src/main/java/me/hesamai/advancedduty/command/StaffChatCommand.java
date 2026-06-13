package me.hesamai.advancedduty.command;

import me.hesamai.advancedduty.Main;
import me.hesamai.advancedduty.lang.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StaffChatCommand implements CommandExecutor {

    private final Main plugin;
    private final LanguageManager lang;

    public StaffChatCommand(Main plugin, LanguageManager lang) {
        this.plugin = plugin;
        this.lang = lang;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("advancedduty.staffchat")) {
            sender.sendMessage(lang.getMessage("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(lang.getMessage("staffchat-usage"));
            return true;
        }

        if (sender instanceof Player) {
            Player player = (Player) sender;
            boolean requireOnDuty = plugin.getConfig().getBoolean("staffchat.require-on-duty", true);
            if (requireOnDuty && !plugin.getDutyManager().isOnDuty(player)) {
                player.sendMessage(lang.getMessage("staffchat-not-on-duty"));
                return true;
            }
        }

        String message = highlightMentions(joinArgs(args, 0));
        String senderName = (sender instanceof Player) ? sender.getName() : "Console";

        String rawFormat = plugin.getConfig().getString("staffchat.format", "&8[&bSTAFF&8] &7{player}&8: &f{message}");
        String prefix = "";
        String suffix = "";

        if (sender instanceof Player && plugin.getLuckPermsHook().isAvailable()) {
            Player player = (Player) sender;
            net.luckperms.api.model.user.User user =
                    plugin.getLuckPermsHook().getLuckPerms().getUserManager()
                            .getUser(player.getUniqueId());
            if (user != null) {
                net.luckperms.api.cacheddata.CachedMetaData meta =
                        user.getCachedData().getMetaData();
                prefix = meta.getPrefix() != null ? meta.getPrefix() : "";
                suffix = meta.getSuffix() != null ? meta.getSuffix() : "";
            }
        }

        Component formatted = parse(
                rawFormat
                        .replace("{player}", senderName)
                        .replace("{message}", message)
                        .replace("{prefix}", prefix)
                        .replace("{suffix}", suffix)
                        .replace("{world}", sender instanceof Player
                                ? ((Player) sender).getWorld().getName() : "")
                        .replace("{displayname}", sender instanceof Player
                                ? ((Player) sender).getDisplayName() : senderName)
        );

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("advancedduty.staffchat.receive")
                    || plugin.getDutyManager().isOnDuty(p)) {
                p.sendMessage(formatted);

                if (plugin.getConfig().getBoolean("staffchat.sound.enabled", true)) {
                    String soundName = plugin.getConfig().getString("staffchat.sound.name", "BLOCK_NOTE_BLOCK_PLING");
                    float volume = (float) plugin.getConfig().getDouble("staffchat.sound.volume", 1.0);
                    float pitch  = (float) plugin.getConfig().getDouble("staffchat.sound.pitch", 1.2);
                    try {
                        p.playSound(p.getLocation(), org.bukkit.Sound.valueOf(soundName), volume, pitch);
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }

        if (plugin.getConfig().getBoolean("staffchat.log-to-console", true)) {
            plugin.getLogger().info("[StaffChat] " + senderName + ": " + message);
        }

        plugin.getWebhookManager().sendStaffChat(senderName, message);

        return true;
    }

    private String highlightMentions(String message) {
        if (!plugin.getConfig().getBoolean("staffchat.mentions.enabled", true)) return message;

        String color = plugin.getConfig().getString("staffchat.mentions.color", "&a");
        String reset = plugin.getConfig().getString("staffchat.mentions.reset-color", "&f");

        for (Player p : Bukkit.getOnlinePlayers()) {
            String name = p.getName();
            if (message.toLowerCase().contains(name.toLowerCase())) {
                message = message.replaceAll("(?i)" + java.util.regex.Pattern.quote(name), color + "@" + name + reset);
                playMentionSound(p);
            }
        }
        return message;
    }

    private void playMentionSound(Player p) {
        if (!plugin.getConfig().getBoolean("staffchat.mentions.sound.enabled", true)) return;
        String soundName = plugin.getConfig().getString("staffchat.mentions.sound.name", "BLOCK_NOTE_BLOCK_PLING");
        float volume = (float) plugin.getConfig().getDouble("staffchat.mentions.sound.volume", 1.0);
        float pitch  = (float) plugin.getConfig().getDouble("staffchat.mentions.sound.pitch", 2.0);
        try {
            p.playSound(p.getLocation(), org.bukkit.Sound.valueOf(soundName), volume, pitch);
        } catch (IllegalArgumentException ignored) {}
    }

    private String joinArgs(String[] args, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (i > start) sb.append(" ");
            sb.append(args[i]);
        }
        return sb.toString();
    }

    private static Component parse(String text) {
        StringBuffer sb = new StringBuffer();
        Matcher m = Pattern.compile("(?i)&([0-9a-fklmnor])").matcher(text);
        while (m.find()) {
            char c = Character.toLowerCase(m.group(1).charAt(0));
            String replacement;
            if      (c == '0') replacement = "<black>";
            else if (c == '1') replacement = "<dark_blue>";
            else if (c == '2') replacement = "<dark_green>";
            else if (c == '3') replacement = "<dark_aqua>";
            else if (c == '4') replacement = "<dark_red>";
            else if (c == '5') replacement = "<dark_purple>";
            else if (c == '6') replacement = "<gold>";
            else if (c == '7') replacement = "<gray>";
            else if (c == '8') replacement = "<dark_gray>";
            else if (c == '9') replacement = "<blue>";
            else if (c == 'a') replacement = "<green>";
            else if (c == 'b') replacement = "<aqua>";
            else if (c == 'c') replacement = "<red>";
            else if (c == 'd') replacement = "<light_purple>";
            else if (c == 'e') replacement = "<yellow>";
            else if (c == 'f') replacement = "<white>";
            else if (c == 'l') replacement = "<bold>";
            else if (c == 'o') replacement = "<italic>";
            else if (c == 'n') replacement = "<underlined>";
            else if (c == 'm') replacement = "<strikethrough>";
            else if (c == 'k') replacement = "<obfuscated>";
            else if (c == 'r') replacement = "<reset>";
            else               replacement = m.group(0);
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return MiniMessage.miniMessage().deserialize(sb.toString());
    }
}