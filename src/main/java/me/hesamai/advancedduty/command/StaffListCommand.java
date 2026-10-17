package me.hesamai.advancedduty.command;

import me.hesamai.advancedduty.Main;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/** {@code /stafflist} - same output as {@code /duty list}. */
public class StaffListCommand implements CommandExecutor {

    private final Main plugin;

    public StaffListCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return plugin.getServer().getPluginCommand("duty") != null
                && plugin.getServer().getPluginCommand("duty").getExecutor().onCommand(sender, command, label, new String[]{"list"});
    }
}
