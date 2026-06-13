package me.hesamai.advancedduty.hook;

import me.hesamai.advancedduty.Main;
import me.hesamai.advancedduty.duty.inventory.DualInventoryProfile;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class LuckPermsHook {

    public enum StaffToggleMode {
        GROUPS,
        PERMISSIONS,
        BOTH;

        public static StaffToggleMode fromString(String raw) {
            if (raw == null) {
                return GROUPS;
            }

            try {
                return StaffToggleMode.valueOf(raw.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                return GROUPS;
            }
        }
    }

    private final Main plugin;
    private final LuckPerms luckPerms;

    public LuckPermsHook(Main plugin, LuckPerms luckPerms) {
        this.plugin = plugin;
        this.luckPerms = luckPerms;
    }

    public LuckPerms getLuckPerms() {
        return luckPerms;
    }

    public boolean isAvailable() {
        return luckPerms != null && plugin.getConfig().getBoolean("hooks.luckperms.enabled", true);
    }

    public boolean isStaffToggleEnabled() {
        return plugin.getConfig().getBoolean("hooks.luckperms.staff-toggle.enabled", true);
    }

    public StaffToggleMode getStaffToggleMode() {
        return StaffToggleMode.fromString(
                plugin.getConfig().getString("hooks.luckperms.staff-toggle.mode", "GROUPS")
        );
    }

    public List<String> getTrackedGroups() {
        return new ArrayList<String>(
                plugin.getConfig().getStringList("hooks.luckperms.staff-toggle.groups")
        );
    }

    public List<String> getTrackedPermissions() {
        return new ArrayList<String>(
                plugin.getConfig().getStringList("hooks.luckperms.staff-toggle.permissions")
        );
    }

    public void disableStaffAccess(Player player, DualInventoryProfile profile) {
        if (!isAvailable() || !isStaffToggleEnabled() || profile == null) {
            return;
        }

        User user = resolveUser(player);
        if (user == null) {
            return;
        }

        StaffToggleMode mode = getStaffToggleMode();

        if (mode == StaffToggleMode.GROUPS || mode == StaffToggleMode.BOTH) {
            List<String> capturedGroups = capturePresentTrackedGroups(user);
            profile.setStoredStaffGroups(capturedGroups);

            for (String group : capturedGroups) {
                user.data().remove(InheritanceNode.builder(group).build());
            }
        }

        if (mode == StaffToggleMode.PERMISSIONS || mode == StaffToggleMode.BOTH) {
            List<String> capturedPermissions = capturePresentTrackedPermissions(user);
            profile.setStoredStaffPermissions(capturedPermissions);

            for (String permission : capturedPermissions) {
                user.data().remove(Node.builder(permission).build());
            }
        }

        luckPerms.getUserManager().saveUser(user);
    }

    public void enableStaffAccess(Player player, DualInventoryProfile profile) {
        if (!isAvailable() || !isStaffToggleEnabled() || profile == null) {
            return;
        }

        User user = resolveUser(player);
        if (user == null) {
            return;
        }

        StaffToggleMode mode = getStaffToggleMode();

        if (mode == StaffToggleMode.GROUPS || mode == StaffToggleMode.BOTH) {
            for (String group : profile.getStoredStaffGroups()) {
                if (!hasGroup(user, group)) {
                    user.data().add(InheritanceNode.builder(group).build());
                }
            }
        }

        if (mode == StaffToggleMode.PERMISSIONS || mode == StaffToggleMode.BOTH) {
            for (String permission : profile.getStoredStaffPermissions()) {
                if (!hasPermissionNode(user, permission)) {
                    user.data().add(Node.builder(permission).build());
                }
            }
        }

        luckPerms.getUserManager().saveUser(user);
    }

    public void initializeStoredAccess(Player player, DualInventoryProfile profile) {
        if (!isAvailable() || !isStaffToggleEnabled() || profile == null) {
            return;
        }

        User user = resolveUser(player);
        if (user == null) {
            return;
        }

        StaffToggleMode mode = getStaffToggleMode();

        if ((mode == StaffToggleMode.GROUPS || mode == StaffToggleMode.BOTH)
                && profile.getStoredStaffGroups().isEmpty()) {
            profile.setStoredStaffGroups(capturePresentTrackedGroups(user));
        }

        if ((mode == StaffToggleMode.PERMISSIONS || mode == StaffToggleMode.BOTH)
                && profile.getStoredStaffPermissions().isEmpty()) {
            profile.setStoredStaffPermissions(capturePresentTrackedPermissions(user));
        }
    }

    private List<String> capturePresentTrackedGroups(User user) {
        Set<String> found = new LinkedHashSet<String>();
        List<String> tracked = getTrackedGroups();

        for (String trackedGroup : tracked) {
            if (hasGroup(user, trackedGroup)) {
                found.add(trackedGroup);
            }
        }

        return new ArrayList<String>(found);
    }

    private List<String> capturePresentTrackedPermissions(User user) {
        Set<String> found = new LinkedHashSet<String>();
        List<String> tracked = getTrackedPermissions();

        for (String trackedPermission : tracked) {
            if (hasPermissionNode(user, trackedPermission)) {
                found.add(trackedPermission);
            }
        }

        return new ArrayList<String>(found);
    }

    private boolean hasGroup(User user, String groupName) {
        if (groupName == null || groupName.trim().isEmpty()) {
            return false;
        }

        for (Node node : user.getNodes()) {
            if (node instanceof InheritanceNode) {
                InheritanceNode inheritanceNode = (InheritanceNode) node;
                if (inheritanceNode.getGroupName().equalsIgnoreCase(groupName)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean hasPermissionNode(User user, String permission) {
        if (permission == null || permission.trim().isEmpty()) {
            return false;
        }

        for (Node node : user.getNodes()) {
            if (!(node instanceof InheritanceNode) && node.getKey().equalsIgnoreCase(permission)) {
                return true;
            }
        }

        return false;
    }

    private User resolveUser(Player player) {
        if (luckPerms == null) {
            return null;
        }

        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user != null) {
            return user;
        }

        try {
            return luckPerms.getUserManager().loadUser(player.getUniqueId()).join();
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to load LuckPerms user for " + player.getName() + ": " + ex.getMessage());
            return null;
        }
    }
}