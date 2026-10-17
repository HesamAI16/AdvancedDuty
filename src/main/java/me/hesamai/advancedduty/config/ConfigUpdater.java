package me.hesamai.advancedduty.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

/**
 * Adds options that exist in the bundled resource but are missing from the server owner's file,
 * keeping every existing value and comment untouched. This is what lets a plugin update introduce
 * new settings without anybody having to delete / regenerate their config.
 */
public final class ConfigUpdater {

    /**
     * Sections that hold user-defined entries (profiles, tool items, goals...). When such a section
     * exists in the user's file we never re-add example entries to it.
     */
    public static final Set<String> DYNAMIC_SECTIONS = new HashSet<>(Arrays.asList(
            "profiles",
            "tools.sets",
            "milestones.goals",
            "display.scoreboard.layouts",
            "discord.bot.commands-custom"
    ));

    private ConfigUpdater() {
    }

    /**
     * @return number of keys that were added, or -1 when the file could not be processed
     */
    public static int update(Plugin plugin, File target, String resourcePath, Set<String> dynamicSections) {
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) return 0;
            YamlConfiguration bundled = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            if (!target.exists()) return 0;

            YamlConfiguration user = new YamlConfiguration();
            user.options().parseComments(true);
            try {
                user.load(target);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Could not read " + target.getName()
                        + " for the automatic update (is the YAML valid?): " + ex.getMessage());
                return -1;
            }

            int added = merge(bundled, user, dynamicSections == null ? DYNAMIC_SECTIONS : dynamicSections);
            int bundledVersion = bundled.getInt("config-version", 0);
            boolean versionChanged = bundledVersion > 0 && user.getInt("config-version", 0) != bundledVersion
                    && resourcePath.equals("config.yml");
            if (versionChanged) user.set("config-version", bundledVersion);

            if (added > 0 || versionChanged) {
                backup(target, bundledVersion);
                user.save(target);
                if (added > 0) {
                    plugin.getLogger().info("Updated " + target.getName() + ": added " + added + " new option(s).");
                }
            }
            return added;
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Config update failed for " + target.getName(), ex);
            return -1;
        }
    }

    /** Copies every key of {@code bundled} that {@code user} lacks into {@code user}. */
    public static int merge(YamlConfiguration bundled, YamlConfiguration user, Set<String> dynamicSections) {
        int added = 0;
        boolean headerSet = !user.options().getHeader().isEmpty();
        if (!headerSet && !bundled.options().getHeader().isEmpty()) {
            user.options().setHeader(bundled.options().getHeader());
        }

        java.util.List<String> missing = new java.util.ArrayList<>();
        for (String key : bundled.getKeys(true)) {
            if (user.contains(key, true)) continue;
            if (insideExistingDynamicSection(key, user, dynamicSections)) continue;
            missing.add(key);

            if (bundled.isConfigurationSection(key)) {
                ConfigurationSection section = bundled.getConfigurationSection(key);
                // An empty section would otherwise never be created because it has no leaves.
                if (section != null && section.getKeys(false).isEmpty()) {
                    user.createSection(key);
                    added++;
                }
            } else {
                user.set(key, bundled.get(key));
                added++;
            }
        }
        // Comments are copied in a second pass so that parent sections already exist.
        for (String key : missing) {
            if (user.contains(key, true)) copyComments(bundled, user, key);
        }
        return added;
    }

    private static boolean insideExistingDynamicSection(String key, YamlConfiguration user, Set<String> dynamicSections) {
        for (String dyn : dynamicSections) {
            if (key.length() > dyn.length() && key.startsWith(dyn + ".") && user.contains(dyn, true)) {
                return true;
            }
        }
        return false;
    }

    private static void copyComments(YamlConfiguration from, YamlConfiguration to, String key) {
        List<String> comments = from.getComments(key);
        if (!comments.isEmpty()) to.setComments(key, comments);
        List<String> inline = from.getInlineComments(key);
        if (!inline.isEmpty()) to.setInlineComments(key, inline);
    }

    private static void backup(File file, int version) {
        try {
            File bak = new File(file.getParentFile(), file.getName() + ".bak-v" + Math.max(version, 0));
            if (!bak.exists()) {
                Files.copy(file.toPath(), bak.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            }
        } catch (IOException ignored) {
            // A failed backup must never block the update itself.
        }
    }
}
