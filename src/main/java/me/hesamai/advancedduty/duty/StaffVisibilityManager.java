package me.hesamai.advancedduty.duty;

import me.hesamai.advancedduty.Main;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.PrefixNode;
import net.luckperms.api.node.types.SuffixNode;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;

public class StaffVisibilityManager {

    private final Main plugin;
    private final LuckPerms luckPerms;

    public StaffVisibilityManager(Main plugin, LuckPerms luckPerms) {
        this.plugin = plugin;
        this.luckPerms = luckPerms;
    }

    public void applyStaffMeta(Player player) {
        if (plugin.getConfig().getBoolean("hooks.luckperms.staff-visibility.glow", true))
            player.setGlowing(true);

        applyNameTag(player, true);
    }

    public void removeStaffMeta(Player player) {
        if (plugin.getConfig().getBoolean("hooks.luckperms.staff-visibility.glow", true))
            player.setGlowing(false);

        applyNameTag(player, false);
    }

    private void applyNameTag(Player player, boolean apply) {
        if (!plugin.getConfig().getBoolean("hooks.luckperms.enabled", true)) return;
        if (!plugin.getConfig().getBoolean("hooks.luckperms.staff-visibility.enabled", true)) return;
        if (luckPerms == null) return;

        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) return;

        int weight = plugin.getConfig().getInt("hooks.luckperms.staff-visibility.name-tag.weight", 100);

        new ArrayList<>(user.data().toCollection()).stream()
                .filter(n -> (n instanceof PrefixNode && ((PrefixNode) n).getPriority() == weight)
                        || (n instanceof SuffixNode && ((SuffixNode) n).getPriority() == weight))
                .forEach(n -> user.data().remove(n));

        if (apply) {
            String prefix = ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("hooks.luckperms.staff-visibility.name-tag.prefix", ""));
            String suffix = ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("hooks.luckperms.staff-visibility.name-tag.suffix", ""));

            if (!prefix.isEmpty()) user.data().add(PrefixNode.builder(prefix, weight).build());
            if (!suffix.isEmpty()) user.data().add(SuffixNode.builder(suffix, weight).build());
        }

        luckPerms.getUserManager().saveUser(user);
    }
}