package me.hesamai.advancedduty.lang;

import me.hesamai.advancedduty.Main;
import me.hesamai.advancedduty.util.MessageUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class LanguageManager {

    private final Main plugin;
    private FileConfiguration languageConfig;
    private String currentLanguage;

    public LanguageManager(Main plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();

        currentLanguage = plugin.getConfig().getString("language", "en").toLowerCase();

        saveLanguageResourceIfNotExists("en.yml");
        saveLanguageResourceIfNotExists("es.yml");
        saveLanguageResourceIfNotExists("ru.yml");
        saveLanguageResourceIfNotExists("zh.yml");

        File langFile = new File(plugin.getDataFolder(), "languages/" + currentLanguage + ".yml");

        if (!langFile.exists()) {
            plugin.getLogger().warning("Language file '" + currentLanguage + ".yml' not found. Falling back to en.yml");
            currentLanguage = "en";
            langFile = new File(plugin.getDataFolder(), "languages/en.yml");
        }

        languageConfig = YamlConfiguration.loadConfiguration(langFile);

        InputStream defaultStream = plugin.getResource("languages/" + currentLanguage + ".yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8)
            );
            languageConfig.setDefaults(defaultConfig);
        }
    }

    private void saveLanguageResourceIfNotExists(String fileName) {
        File folder = new File(plugin.getDataFolder(), "languages");

        if (!folder.exists()) {
            folder.mkdirs();
        }

        File file = new File(folder, fileName);

        if (!file.exists()) {
            plugin.saveResource("languages/" + fileName, false);
        }
    }

    public void setLanguage(String lang) {
        File langFile = new File(plugin.getDataFolder(), "languages/" + lang + ".yml");

        if (!langFile.exists()) {
            plugin.getLogger().warning("Language file not found: " + lang);
            return;
        }

        plugin.getConfig().set("language", lang.toLowerCase());
        plugin.saveConfig();

        currentLanguage = lang.toLowerCase();
        load();
    }

    public File[] getLanguageFiles() {
        File folder = new File(plugin.getDataFolder(), "languages");

        if (!folder.exists()) {
            folder.mkdirs();
        }

        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
        return files != null ? files : new File[0];
    }

    public String getMessage(String path) {
        String prefix = languageConfig.getString("prefix", "&8[&bAdvancedDuty&8] ");
        String message = languageConfig.getString("messages." + path, "&cMissing language key: " + path);
        return MessageUtil.color(prefix + message);
    }

    public String getMessageWithoutPrefix(String path) {
        String message = languageConfig.getString("messages." + path, "&cMissing language key: " + path);
        return MessageUtil.color(message);
    }

    public String getMessage(String path, String p1, String v1, String p2, String v2) {
        Map<String, String> ph = new HashMap<String, String>();
        ph.put(p1, v1);
        ph.put(p2, v2);
        return getMessage(path, ph);
    }

    public String getMessage(String path, String p1, String v1) {
        return getMessage(path).replace(p1, v1);
    }

    public String getMessage(String path, Map<String, String> placeholders) {
        String text = getMessage(path);

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            text = text.replace(entry.getKey(), entry.getValue());
        }

        return text;
    }

    public String getPlaceholder(String path) {
        String raw = languageConfig.getString("placeholders." + path, "");
        return MessageUtil.color(raw);
    }

    public String getCurrentLanguage() {
        return currentLanguage;
    }
}