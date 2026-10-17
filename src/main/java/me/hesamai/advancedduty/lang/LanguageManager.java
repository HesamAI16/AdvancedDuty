package me.hesamai.advancedduty.lang;

import me.hesamai.advancedduty.Main;
import me.hesamai.advancedduty.config.ConfigUpdater;
import me.hesamai.advancedduty.duty.playtime.PlaytimeFormatter;
import me.hesamai.advancedduty.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads language files. Lookup order for every key:
 * <ol>
 *   <li>the file in {@code plugins/AdvancedDuty/languages/<lang>.yml}</li>
 *   <li>the bundled copy of that language</li>
 *   <li>the server owner's {@code en.yml}</li>
 *   <li>the bundled {@code en.yml}</li>
 * </ol>
 * so a missing / outdated translation never shows up as "Missing language key".
 */
public class LanguageManager {

    public static final String[] BUNDLED = {"en", "es", "ru", "zh", "fa"};

    private final Main plugin;
    private FileConfiguration language;
    private FileConfiguration fallback;
    private String currentLanguage = "en";

    public LanguageManager(Main plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ loading

    public void load() {
        currentLanguage = plugin.getConfig().getString("language", "en").toLowerCase(Locale.ROOT);

        File folder = new File(plugin.getDataFolder(), "languages");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create the languages folder.");
        }

        for (String code : BUNDLED) {
            File file = new File(folder, code + ".yml");
            if (!file.exists()) {
                plugin.saveResource("languages/" + code + ".yml", false);
            } else if (plugin.getConfig().getBoolean("auto-update-files", true)) {
                ConfigUpdater.update(plugin, file, "languages/" + code + ".yml", java.util.Collections.emptySet());
            }
        }

        File langFile = new File(folder, currentLanguage + ".yml");
        if (!langFile.exists()) {
            plugin.getLogger().warning("Language file '" + currentLanguage + ".yml' not found. Falling back to en.yml");
            currentLanguage = "en";
            langFile = new File(folder, "en.yml");
        }

        language = loadFile(langFile);
        YamlConfiguration bundledCurrent = loadBundled(currentLanguage);
        if (bundledCurrent != null) language.setDefaults(bundledCurrent);

        // fallback chain for anything still missing -> English
        File en = new File(folder, "en.yml");
        fallback = en.exists() ? loadFile(en) : new YamlConfiguration();
        YamlConfiguration bundledEn = loadBundled("en");
        if (bundledEn != null) fallback.setDefaults(bundledEn);

        PlaytimeFormatter.setUnits(
                rawString("placeholders.time.day", "d"),
                rawString("placeholders.time.hour", "h"),
                rawString("placeholders.time.minute", "m"),
                rawString("placeholders.time.second", "s"));
    }

    private FileConfiguration loadFile(File file) {
        YamlConfiguration cfg = new YamlConfiguration();
        try {
            cfg.load(file);
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not read language file " + file.getName() + ": " + ex.getMessage());
        }
        return cfg;
    }

    private YamlConfiguration loadBundled(String code) {
        try (InputStream in = plugin.getResource("languages/" + code + ".yml")) {
            if (in == null) return null;
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return null;
        }
    }

    // ------------------------------------------------------------------ raw lookups

    private String rawString(String path, String def) {
        String v = language == null ? null : language.getString(path);
        if (v == null && fallback != null) v = fallback.getString(path);
        return v == null ? def : v;
    }

    private List<String> rawList(String path) {
        List<String> v = language == null ? null : language.getStringList(path);
        if ((v == null || v.isEmpty()) && fallback != null) v = fallback.getStringList(path);
        return v == null ? new ArrayList<String>() : v;
    }

    /** Uncoloured, unprefixed message text exactly as written in the language file. */
    public String text(String path) {
        return rawString("messages." + path, "&cMissing language key: " + path);
    }

    public boolean hasMessage(String path) {
        return rawString("messages." + path, null) != null;
    }

    // ------------------------------------------------------------------ message API (new)

    /** Prefixed, coloured message with {@code {placeholder}, value} pairs applied. */
    public String msg(String path, Object... kv) {
        String body = text(path);
        if (body.trim().isEmpty()) return "";
        return MessageUtil.color(replace(prefix() + body, kv));
    }

    /** Coloured message without the prefix. */
    public String raw(String path, Object... kv) {
        String body = text(path);
        if (body.trim().isEmpty()) return "";
        return MessageUtil.color(replace(body, kv));
    }

    /** Coloured multi-line message list (messages.&lt;path&gt; is a YAML list). */
    public List<String> list(String path, Object... kv) {
        List<String> out = new ArrayList<>();
        for (String line : rawList("messages." + path)) {
            out.add(MessageUtil.color(replace(line, kv)));
        }
        return out;
    }

    /** GUI / misc strings stored under {@code gui.*}. */
    public String gui(String path, Object... kv) {
        return MessageUtil.color(replace(rawString("gui." + path, path), kv));
    }

    public List<String> guiList(String path, Object... kv) {
        List<String> out = new ArrayList<>();
        for (String line : rawList("gui." + path)) out.add(MessageUtil.color(replace(line, kv)));
        return out;
    }

    /** Sends a prefixed message; an empty translation sends nothing (lets owners silence a message). */
    public void send(CommandSender to, String path, Object... kv) {
        if (to == null) return;
        String m = msg(path, kv);
        if (!m.isEmpty()) to.sendMessage(m);
    }

    public void sendRaw(CommandSender to, String path, Object... kv) {
        if (to == null) return;
        String m = raw(path, kv);
        if (!m.isEmpty()) to.sendMessage(m);
    }

    public void sendList(CommandSender to, String path, Object... kv) {
        if (to == null) return;
        for (String line : list(path, kv)) to.sendMessage(line);
    }

    public String prefix() {
        return rawString("prefix", "&8[&bAdvancedDuty&8] ");
    }

    private static String replace(String text, Object... kv) {
        if (kv == null || kv.length == 0) return text;
        String out = text;
        for (int i = 0; i + 1 < kv.length; i += 2) {
            String key = String.valueOf(kv[i]);
            if (!key.startsWith("{")) key = "{" + key + "}";
            out = out.replace(key, String.valueOf(kv[i + 1]));
        }
        return out;
    }

    // ------------------------------------------------------------------ legacy API (kept)

    public String getMessage(String path) {
        return msg(path);
    }

    public String getMessageWithoutPrefix(String path) {
        return raw(path);
    }

    public String getMessage(String path, String p1, String v1) {
        return msg(path, p1, v1);
    }

    public String getMessage(String path, String p1, String v1, String p2, String v2) {
        return msg(path, p1, v1, p2, v2);
    }

    public String getMessage(String path, Map<String, String> placeholders) {
        Object[] kv = new Object[placeholders.size() * 2];
        int i = 0;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            kv[i++] = e.getKey();
            kv[i++] = e.getValue();
        }
        return msg(path, kv);
    }

    public String getPlaceholder(String path) {
        return MessageUtil.color(rawString("placeholders." + path, ""));
    }

    // ------------------------------------------------------------------ language management

    public String getCurrentLanguage() {
        return currentLanguage;
    }

    public void setLanguage(String lang) {
        File langFile = new File(plugin.getDataFolder(), "languages/" + lang + ".yml");
        if (!langFile.exists()) {
            plugin.getLogger().warning("Language file not found: " + lang);
            return;
        }
        plugin.getConfig().set("language", lang.toLowerCase(Locale.ROOT));
        plugin.saveConfig();
        load();
    }

    public File[] getLanguageFiles() {
        File folder = new File(plugin.getDataFolder(), "languages");
        if (!folder.exists()) folder.mkdirs();
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return new File[0];
        Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return files;
    }

    /** Human readable language name from {@code meta.name} (falls back to the upper-cased code). */
    public String getLanguageName(String code) {
        File f = new File(plugin.getDataFolder(), "languages/" + code + ".yml");
        if (f.exists()) {
            String name = YamlConfiguration.loadConfiguration(f).getString("meta.name");
            if (name != null && !name.isEmpty()) return name;
        }
        YamlConfiguration bundled = loadBundled(code);
        if (bundled != null && bundled.getString("meta.name") != null) return bundled.getString("meta.name");
        return code.toUpperCase(Locale.ROOT);
    }

    public String getLanguageIcon(String code) {
        File f = new File(plugin.getDataFolder(), "languages/" + code + ".yml");
        if (f.exists()) {
            String icon = YamlConfiguration.loadConfiguration(f).getString("meta.icon");
            if (icon != null && !icon.isEmpty()) return icon;
        }
        YamlConfiguration bundled = loadBundled(code);
        if (bundled != null && bundled.getString("meta.icon") != null) return bundled.getString("meta.icon");
        return "PAPER";
    }

    /** Convenience used by tests / diagnostics. */
    public Map<String, String> snapshot(String... paths) {
        Map<String, String> out = new HashMap<>();
        for (String p : paths) out.put(p, rawString(p, ""));
        return out;
    }
}
