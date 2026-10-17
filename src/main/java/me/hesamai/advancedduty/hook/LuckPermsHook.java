package me.hesamai.advancedduty.hook;

import me.hesamai.advancedduty.Main;
import me.hesamai.advancedduty.duty.inventory.DualInventoryProfile;
import me.hesamai.advancedduty.util.MessageUtil;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.PrefixNode;
import net.luckperms.api.node.types.SuffixNode;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Everything that touches LuckPerms: the staff group / permission toggle, the per-state meta rules,
 * the duty prefix and the off-duty name tag. Every method is a no-op when LuckPerms is missing, and
 * all prefix / tag changes are <b>transient</b>, so nothing stays in the LuckPerms database if the
 * server crashes.
 */
public class LuckPermsHook {

    public enum StaffToggleMode {
        GROUPS, PERMISSIONS, BOTH;

        public static StaffToggleMode fromString(String raw) {
            if (raw == null) return GROUPS;
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return GROUPS;
            }
        }
    }

    private final Main plugin;
    private final LuckPerms luckPerms;
    private final Map<UUID, PrefixNode> dutyPrefixes = new ConcurrentHashMap<>();
    private final Map<UUID, List<Node>> tagNodes = new ConcurrentHashMap<>();

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
        return isAvailable() && plugin.getConfig().getBoolean("hooks.luckperms.staff-toggle.enabled", true);
    }

    public StaffToggleMode getStaffToggleMode() {
        return StaffToggleMode.fromString(plugin.getConfig().getString("hooks.luckperms.staff-toggle.mode", "GROUPS"));
    }

    public List<String> getTrackedGroups() {
        return new ArrayList<>(plugin.getConfig().getStringList("hooks.luckperms.staff-toggle.groups"));
    }

    public List<String> getTrackedPermissions() {
        return new ArrayList<>(plugin.getConfig().getStringList("hooks.luckperms.staff-toggle.permissions"));
    }

    /** The cached LuckPerms user of an online player (never blocks), or {@code null}. */
    public User user(UUID uuid) {
        if (luckPerms == null) return null;
        return luckPerms.getUserManager().getUser(uuid);
    }

    /** Prefix of the player as LuckPerms sees it (empty without LuckPerms). */
    public String prefixOf(UUID uuid) {
        return meta(uuid, true);
    }

    public String suffixOf(UUID uuid) {
        return meta(uuid, false);
    }

    private String meta(UUID uuid, boolean prefix) {
        User user = user(uuid);
        if (user == null || !isAvailable()) return "";
        try {
            CachedMetaData data = user.getCachedData().getMetaData();
            String v = prefix ? data.getPrefix() : data.getSuffix();
            return v == null ? "" : v;
        } catch (Throwable ex) {
            return "";
        }
    }

    /** Primary LuckPerms group, handy for scoreboards / chat formats. */
    public String primaryGroup(UUID uuid) {
        User user = user(uuid);
        return user == null ? "" : user.getPrimaryGroup();
    }

    // ------------------------------------------------------------------ staff toggle (groups / permissions)

    /** Takes the tracked staff groups / permissions away (going OFF duty) and remembers them in the profile. */
    public void disableStaffAccess(Player player, DualInventoryProfile profile) {
        if (!isStaffToggleEnabled() || profile == null) return;
        User user = user(player.getUniqueId());
        if (user == null) {
            plugin.getLogger().warning("LuckPerms user of " + player.getName() + " is not loaded - staff access was not changed.");
            return;
        }
        StaffToggleMode mode = getStaffToggleMode();

        if (mode != StaffToggleMode.PERMISSIONS) {
            List<String> present = presentGroups(user);
            // never overwrite a good backup with an empty list
            if (!present.isEmpty()) profile.setStoredStaffGroups(present);
            for (String group : present) removeGroup(user, group);
        }
        if (mode != StaffToggleMode.GROUPS) {
            List<String> present = presentPermissions(user);
            if (!present.isEmpty()) profile.setStoredStaffPermissions(present);
            for (String permission : present) removePermission(user, permission);
        }
        luckPerms.getUserManager().saveUser(user);
    }

    /** Gives the remembered staff groups / permissions back (going ON duty). */
    public void enableStaffAccess(Player player, DualInventoryProfile profile) {
        if (!isStaffToggleEnabled() || profile == null) return;
        User user = user(player.getUniqueId());
        if (user == null) {
            plugin.getLogger().warning("LuckPerms user of " + player.getName() + " is not loaded - staff access was not changed.");
            return;
        }
        StaffToggleMode mode = getStaffToggleMode();

        if (mode != StaffToggleMode.PERMISSIONS) {
            for (String group : profile.getStoredStaffGroups()) {
                if (!hasGroup(user, group)) user.data().add(InheritanceNode.builder(group).build());
            }
        }
        if (mode != StaffToggleMode.GROUPS) {
            for (String permission : profile.getStoredStaffPermissions()) {
                if (!hasPermissionNode(user, permission)) user.data().add(Node.builder(permission).build());
            }
        }
        luckPerms.getUserManager().saveUser(user);
    }

    /** {@code true} if the player currently owns any of the tracked groups / permissions. */
    public boolean hasTrackedAccess(UUID uuid) {
        User user = user(uuid);
        if (user == null || !isStaffToggleEnabled()) return false;
        StaffToggleMode mode = getStaffToggleMode();
        if (mode != StaffToggleMode.PERMISSIONS && !presentGroups(user).isEmpty()) return true;
        return mode != StaffToggleMode.GROUPS && !presentPermissions(user).isEmpty();
    }

    private List<String> presentGroups(User user) {
        Set<String> found = new LinkedHashSet<>();
        for (String g : getTrackedGroups()) if (hasGroup(user, g)) found.add(g);
        return new ArrayList<>(found);
    }

    private List<String> presentPermissions(User user) {
        Set<String> found = new LinkedHashSet<>();
        for (String p : getTrackedPermissions()) if (hasPermissionNode(user, p)) found.add(p);
        return new ArrayList<>(found);
    }

    private static boolean hasGroup(User user, String group) {
        if (group == null || group.trim().isEmpty()) return false;
        for (InheritanceNode n : user.getNodes(NodeType.INHERITANCE)) {
            if (n.getGroupName().equalsIgnoreCase(group)) return true;
        }
        return false;
    }

    private static boolean hasPermissionNode(User user, String permission) {
        if (permission == null || permission.trim().isEmpty()) return false;
        for (Node n : user.getNodes(NodeType.PERMISSION)) {
            if (n.getKey().equalsIgnoreCase(permission)) return true;
        }
        return false;
    }

    /** Removes the group however the node was created (contexts / expiry do not matter). */
    private static void removeGroup(User user, String group) {
        user.data().clear(NodeType.INHERITANCE.predicate(n -> n.getGroupName().equalsIgnoreCase(group)));
    }

    private static void removePermission(User user, String permission) {
        user.data().clear(NodeType.PERMISSION.predicate(n -> n.getKey().equalsIgnoreCase(permission)));
    }

    // ------------------------------------------------------------------ duty-meta rules

    /** Applies the {@code hooks.luckperms.duty-meta.on-duty / off-duty} rules. */
    public void applyMeta(Player player, boolean onDuty) {
        if (!isAvailable() || !plugin.getConfig().getBoolean("hooks.luckperms.duty-meta.enabled", true)) return;
        User user = user(player.getUniqueId());
        if (user == null) return;

        ConfigurationSection root = plugin.getConfig().getConfigurationSection("hooks.luckperms.duty-meta");
        if (root == null) return;
        String state = onDuty ? "on-duty" : "off-duty";
        String other = onDuty ? "off-duty" : "on-duty";
        ConfigurationSection sec = root.getConfigurationSection(state);
        ConfigurationSection opposite = root.getConfigurationSection(other);
        if (sec == null) return;

        process(user, sec.getList("groups"), opposite == null ? null : opposite.getList("groups"), true);
        process(user, sec.getList("permissions"), opposite == null ? null : opposite.getList("permissions"), false);
        luckPerms.getUserManager().saveUser(user);
    }

    private void process(User user, List<?> entries, List<?> oppositeEntries, boolean group) {
        if (entries == null) return;
        for (Object raw : entries) {
            String name;
            String action = "ADD";
            boolean value = true;
            if (raw instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) raw;
                Object n = map.get(group ? "name" : "node");
                name = n == null ? null : n.toString();
                Object a = map.get("action");
                if (a != null) action = a.toString().toUpperCase(Locale.ROOT);
                Object v = map.get("value");
                if (v != null) value = Boolean.parseBoolean(v.toString());
            } else if (raw instanceof String) {
                name = (String) raw;
            } else {
                continue;
            }
            if (name == null || name.isEmpty()) continue;

            switch (action) {
                case "REMOVE":
                    remove(user, name, group);
                    break;
                case "OVERWRITE":
                    remove(user, name, group);
                    add(user, name, value, group);
                    break;
                case "SYNC":
                    // keep this entry, drop everything the opposite state would have added
                    removeListed(user, oppositeEntries, group, name);
                    remove(user, name, group);
                    add(user, name, value, group);
                    break;
                case "ADD":
                default:
                    if (group ? !hasGroup(user, name) : !hasPermissionNode(user, name)) add(user, name, value, group);
                    break;
            }
        }
    }

    private void removeListed(User user, List<?> list, boolean group, String except) {
        if (list == null) return;
        for (Object raw : list) {
            String n = null;
            if (raw instanceof Map) {
                Object o = ((Map<?, ?>) raw).get(group ? "name" : "node");
                n = o == null ? null : o.toString();
            } else if (raw instanceof String) {
                n = (String) raw;
            }
            if (n != null && !n.equalsIgnoreCase(except)) remove(user, n, group);
        }
    }

    private static void add(User user, String name, boolean value, boolean group) {
        if (group) user.data().add(InheritanceNode.builder(name).build());
        else user.data().add(Node.builder(name).value(value).build());
    }

    private static void remove(User user, String name, boolean group) {
        if (group) removeGroup(user, name);
        else removePermission(user, name);
    }

    // ------------------------------------------------------------------ duty prefix (transient)

    public void applyDutyPrefix(Player player) {
        if (!isAvailable() || player == null || !player.isOnline()) return;
        if (!plugin.getConfig().getBoolean("hooks.luckperms.duty-prefix.enabled", true)) return;
        User user = user(player.getUniqueId());
        if (user == null) return;

        removeDutyPrefix(player);

        String fixed = plugin.getConfig().getString("hooks.luckperms.duty-prefix.fixed", "");
        String prefix = fixed == null ? "" : MessageUtil.color(fixed);

        if (prefix.isEmpty()) {
            String groupName = null;
            for (String g : plugin.getConfig().getStringList("hooks.luckperms.staff-toggle.groups")) {
                if (player.hasPermission("group." + g)) {
                    groupName = g;
                    break;
                }
            }
            if (groupName == null) return;
            Group group = luckPerms.getGroupManager().getGroup(groupName);
            if (group == null) return;
            QueryOptions options = luckPerms.getContextManager().getQueryOptions(user)
                    .orElseGet(() -> luckPerms.getContextManager().getStaticQueryOptions());
            String p = group.getCachedData().getMetaData(options).getPrefix();
            if (p == null || p.isEmpty()) return;
            prefix = p;
        }

        QueryOptions options = luckPerms.getContextManager().getQueryOptions(user)
                .orElseGet(() -> luckPerms.getContextManager().getStaticQueryOptions());
        int highest = 0;
        for (Integer prio : user.getCachedData().getMetaData(options).getPrefixes().keySet()) {
            if (prio != null && prio > highest) highest = prio;
        }
        int priority = highest >= Integer.MAX_VALUE - 1 ? Integer.MAX_VALUE : highest + 1;

        PrefixNode node = PrefixNode.builder(prefix, priority).build();
        user.transientData().add(node);
        dutyPrefixes.put(player.getUniqueId(), node);
    }

    public void removeDutyPrefix(Player player) {
        if (player == null) return;
        PrefixNode old = dutyPrefixes.remove(player.getUniqueId());
        User user = user(player.getUniqueId());
        if (user != null && old != null) user.transientData().remove(old);
    }

    // ------------------------------------------------------------------ off-duty name tag (transient)

    /** Shows (or removes) the staff name tag. Used for staff that are NOT on duty. */
    public void setStaffTag(Player player, boolean show) {
        if (!isAvailable() || player == null) return;
        User user = user(player.getUniqueId());
        if (user == null) return;

        List<Node> old = tagNodes.remove(player.getUniqueId());
        if (old != null) for (Node n : old) user.transientData().remove(n);

        if (!show) return;
        if (!plugin.getConfig().getBoolean("hooks.luckperms.staff-visibility.enabled", true)) return;
        if (!plugin.getConfig().getBoolean("hooks.luckperms.staff-visibility.name-tag.enabled", true)) return;

        int weight = plugin.getConfig().getInt("hooks.luckperms.staff-visibility.name-tag.weight", 20);
        String prefix = MessageUtil.color(plugin.getConfig().getString("hooks.luckperms.staff-visibility.name-tag.prefix", ""));
        String suffix = MessageUtil.color(plugin.getConfig().getString("hooks.luckperms.staff-visibility.name-tag.suffix", ""));

        List<Node> added = new ArrayList<>();
        if (!prefix.isEmpty()) added.add(PrefixNode.builder(prefix, weight).build());
        if (!suffix.isEmpty()) added.add(SuffixNode.builder(suffix, weight).build());
        for (Node n : added) user.transientData().add(n);
        if (!added.isEmpty()) tagNodes.put(player.getUniqueId(), added);
    }

    /** Drops every transient node of a player (quit). */
    public void forget(UUID uuid) {
        User user = user(uuid);
        PrefixNode p = dutyPrefixes.remove(uuid);
        List<Node> t = tagNodes.remove(uuid);
        if (user == null) return;
        if (p != null) user.transientData().remove(p);
        if (t != null) for (Node n : t) user.transientData().remove(n);
    }

    public void clearAllTransient() {
        for (UUID id : new ArrayList<>(dutyPrefixes.keySet())) forget(id);
        for (UUID id : new ArrayList<>(tagNodes.keySet())) forget(id);
    }
}
