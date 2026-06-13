package me.hesamai.advancedduty.duty;

import me.hesamai.advancedduty.Main;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.PermissionNode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.List;

public class DutyMetaManager {

    private final Main plugin;
    private final LuckPerms luckPerms;

    public DutyMetaManager(Main plugin, LuckPerms luckPerms) {
        this.plugin = plugin;
        this.luckPerms = luckPerms;
    }

    public void applyOnDuty(Player player)  { apply(player, "on-duty"); }
    public void applyOffDuty(Player player) { apply(player, "off-duty"); }

    private void apply(Player player, String section) {
        if (!isEnabled()) return;
        if (luckPerms == null) return;

        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) return;

        ConfigurationSection sec = getSection(section);
        if (sec == null) return;

        processEntries(user, sec.getMapList("groups"), true);
        processEntries(user, sec.getMapList("permissions"), false);

        luckPerms.getUserManager().saveUser(user);
    }

    private void processEntries(User user, List<?> entries, boolean isGroup) {
        for (Object raw : entries) {
            if (!(raw instanceof java.util.Map)) continue;
            java.util.Map<Object, Object> entry = (java.util.Map<Object, Object>) raw;

            String name   = (String) entry.get(isGroup ? "name" : "node");
            String action = entry.getOrDefault("action", "ADD").toString().toUpperCase();
            boolean value = entry.getOrDefault("value", true).toString().equalsIgnoreCase("true");

            if (name == null || name.isEmpty()) continue;

            Node node = isGroup
                    ? InheritanceNode.builder(name).build()
                    : PermissionNode.builder(name).value(value).build();

            switch (action) {
                case "ADD":
                    user.data().add(node);
                    break;
                case "REMOVE":
                    user.data().remove(node);
                    break;
                case "OVERWRITE":
                    removeFromAllSections(user, name, isGroup);
                    user.data().add(node);
                    break;
                case "SYNC":
                    removeFromAllSections(user, name, isGroup);
                    break;
            }
        }
    }

    private void removeFromAllSections(User user, String name, boolean isGroup) {
        for (String sec : new String[]{"on-duty", "off-duty"}) {
            ConfigurationSection section = getSection(sec);
            if (section == null) continue;
            List<?> list = isGroup ? section.getMapList("groups") : section.getMapList("permissions");
            for (Object raw : list) {
                if (!(raw instanceof java.util.Map)) continue;
                java.util.Map<Object, Object> e = (java.util.Map<Object, Object>) raw;
                String n = (String) e.get(isGroup ? "name" : "node");
                if (!name.equals(n)) continue;
                boolean val = e.getOrDefault("value", true).toString().equalsIgnoreCase("true");
                Node node = isGroup
                        ? InheritanceNode.builder(n).build()
                        : PermissionNode.builder(n).value(val).build();
                user.data().remove(node);
            }
        }
    }

    private ConfigurationSection getSection(String state) {
        return plugin.getConfig().getConfigurationSection(
                "hooks.luckperms.duty-meta." + state
        );
    }

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("hooks.luckperms.enabled", true)
                && plugin.getConfig().getBoolean("hooks.luckperms.duty-meta.enabled", true);
    }
}