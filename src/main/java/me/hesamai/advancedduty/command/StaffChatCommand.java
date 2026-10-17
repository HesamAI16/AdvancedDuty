package me.hesamai.advancedduty.command;

import me.hesamai.advancedduty.Main;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;

/** {@code /staffchat <message>} sends a message, {@code /staffchat} alone toggles the sticky chat mode. */
public class StaffChatCommand implements CommandExecutor {

    private final Main plugin;

    public StaffChatCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("advancedduty.staffchat")) {
            plugin.getLanguageManager().send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                plugin.getLanguageManager().send(sender, "staffchat-usage", "{label}", label);
                return true;
            }
            boolean on = plugin.getStaffChat().toggleMode((Player) sender);
            plugin.getLanguageManager().send(sender, on ? "staffchat-mode-on" : "staffchat-mode-off");
            return true;
        }
        if (args.length == 1 && sender instanceof Player
                && (args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("off"))) {
            boolean on = args[0].equalsIgnoreCase("on");
            plugin.getStaffChat().setMode((Player) sender, on);
            plugin.getLanguageManager().send(sender, on ? "staffchat-mode-on" : "staffchat-mode-off");
            return true;
        }
        plugin.getStaffChat().send(sender, String.join(" ", Arrays.asList(args)));
        return true;
    }
}
