package me.hesamai.advancedduty.duty.profile;

import me.hesamai.advancedduty.Main;
import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * What happens to a staff member when going on duty. Values come from {@code duty.apply} and can be
 * overridden by any number of named {@code profiles.<name>} that are chosen by permission.
 */
public final class ProfileConfig {

    private final String name;
    private final int priority;
    private final String permission;

    private GameMode gameMode;
    private boolean flight;
    private boolean heal;
    private boolean clearPotions;
    private boolean vanish;
    private boolean godMode;
    private boolean glow;
    private final List<PotionEffect> effects = new ArrayList<>();
    private final List<String> commandsOn = new ArrayList<>();
    private final List<String> commandsOff = new ArrayList<>();
    private String tools = "default";
    private String scoreboardLayout = "default";
    private String tabPrefix = "";
    private String tabSuffix = "";
    private String displayName = "";

    private ProfileConfig(String name, int priority, String permission) {
        this.name = name;
        this.priority = priority;
        this.permission = permission;
    }

    public String getName() { return name; }
    public int getPriority() { return priority; }
    public String getPermission() { return permission; }
    /** {@code null} = leave the game mode alone. */
    public GameMode getGameMode() { return gameMode; }
    public boolean isFlight() { return flight; }
    public boolean isHeal() { return heal; }
    public boolean isClearPotions() { return clearPotions; }
    public boolean isVanish() { return vanish; }
    public boolean isGodMode() { return godMode; }
    public boolean isGlow() { return glow; }
    public List<PotionEffect> getEffects() { return effects; }
    public List<String> getCommandsOn() { return commandsOn; }
    public List<String> getCommandsOff() { return commandsOff; }
    public String getTools() { return tools; }
    public String getScoreboardLayout() { return scoreboardLayout; }
    public String getTabPrefix() { return tabPrefix; }
    public String getTabSuffix() { return tabSuffix; }
    public String getDisplayName() { return displayName; }

    // ------------------------------------------------------------------ loading

    /** Picks the highest-priority profile the player may use. */
    public static ProfileConfig resolve(Main plugin, Player player) {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("profiles");
        ProfileConfig best = null;
        if (root != null) {
            List<ProfileConfig> all = new ArrayList<>();
            for (String key : root.getKeys(false)) {
                ConfigurationSection sec = root.getConfigurationSection(key);
                if (sec == null || !sec.getBoolean("enabled", true)) continue;
                all.add(load(plugin, key, sec));
            }
            all.sort(Comparator.comparingInt(ProfileConfig::getPriority).reversed());
            for (ProfileConfig p : all) {
                if (p.permission == null || p.permission.isEmpty() || player.hasPermission(p.permission)) {
                    best = p;
                    break;
                }
            }
        }
        return best != null ? best : defaults(plugin);
    }

    /** The profile built only from {@code duty.apply}. */
    public static ProfileConfig defaults(Main plugin) {
        ProfileConfig p = new ProfileConfig("default", -1, "");
        p.fill(plugin.getConfig().getConfigurationSection("duty.apply"), null);
        return p;
    }

    private static ProfileConfig load(Main plugin, String key, ConfigurationSection sec) {
        ProfileConfig p = new ProfileConfig(key, sec.getInt("priority", 0), sec.getString("permission", ""));
        p.fill(plugin.getConfig().getConfigurationSection("duty.apply"), sec);
        return p;
    }

    /** Defaults first, then the profile's own keys override them. */
    private void fill(ConfigurationSection defaults, ConfigurationSection own) {
        apply(defaults);
        if (own != null) apply(own);
    }

    private void apply(ConfigurationSection s) {
        if (s == null) return;

        if (s.isConfigurationSection("gamemode")) {
            // legacy layout: gamemode: {enabled, mode}
            if (s.getBoolean("gamemode.enabled", false)) gameMode = parseMode(s.getString("gamemode.mode", "CREATIVE"));
            else gameMode = null;
        } else if (s.contains("gamemode")) {
            gameMode = parseMode(s.getString("gamemode", "NONE"));
        }
        if (s.contains("flight")) flight = s.getBoolean("flight");
        if (s.contains("heal")) heal = s.getBoolean("heal");
        if (s.contains("clear-active-potions")) clearPotions = s.getBoolean("clear-active-potions");
        if (s.contains("vanish")) vanish = s.getBoolean("vanish");
        if (s.contains("god-mode")) godMode = s.getBoolean("god-mode");
        if (s.contains("glow")) glow = s.getBoolean("glow");
        if (s.contains("tools")) tools = s.getString("tools", "default");
        if (s.contains("scoreboard")) scoreboardLayout = s.getString("scoreboard", "default");
        if (s.contains("tab-prefix")) tabPrefix = s.getString("tab-prefix", "");
        if (s.contains("tab-suffix")) tabSuffix = s.getString("tab-suffix", "");
        if (s.contains("display-name")) displayName = s.getString("display-name", "");

        if (s.contains("potion-effects")) {
            effects.clear();
            for (String raw : s.getStringList("potion-effects")) {
                PotionEffect e = parseEffect(raw);
                if (e != null) effects.add(e);
            }
        }
        if (s.contains("commands-on")) {
            commandsOn.clear();
            commandsOn.addAll(s.getStringList("commands-on"));
        }
        if (s.contains("commands-off")) {
            commandsOff.clear();
            commandsOff.addAll(s.getStringList("commands-off"));
        }
    }

    private static GameMode parseMode(String raw) {
        if (raw == null) return null;
        String v = raw.trim().toUpperCase(Locale.ROOT);
        if (v.isEmpty() || v.equals("NONE") || v.equals("KEEP") || v.equals("FALSE")) return null;
        try {
            return GameMode.valueOf(v);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** {@code NAME:amplifier[:seconds]}, e.g. {@code NIGHT_VISION:0} or {@code SPEED:1:600}. */
    @SuppressWarnings("deprecation")
    public static PotionEffect parseEffect(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String[] parts = raw.trim().split(":");
        PotionEffectType type = PotionEffectType.getByName(parts[0].trim().toUpperCase(Locale.ROOT));
        if (type == null) return null;
        int amp = 0;
        int seconds = -1;
        try {
            if (parts.length > 1) amp = Math.max(0, Integer.parseInt(parts[1].trim()));
            if (parts.length > 2) seconds = Integer.parseInt(parts[2].trim());
        } catch (NumberFormatException ex) {
            return null;
        }
        int ticks = seconds <= 0 ? Integer.MAX_VALUE : seconds * 20;
        return new PotionEffect(type, ticks, amp, false, false, true);
    }
}
