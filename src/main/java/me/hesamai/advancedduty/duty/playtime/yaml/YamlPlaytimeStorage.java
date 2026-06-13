package me.hesamai.advancedduty.duty.playtime.yaml;

import me.hesamai.advancedduty.Main;
import me.hesamai.advancedduty.duty.playtime.PlaytimeStorage;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class YamlPlaytimeStorage implements PlaytimeStorage {

    private final File folder;

    public YamlPlaytimeStorage(Main plugin) {
        this.folder = new File(plugin.getDataFolder(), "data/playtimes");
        if (!folder.exists()) folder.mkdirs();
    }

    @Override
    public long load(UUID uuid) {
        File file = fileFor(uuid);
        if (!file.exists()) return 0L;
        return YamlConfiguration.loadConfiguration(file).getLong("playtime", 0L);
    }

    @Override
    public Map<UUID, Long> loadAll() {
        Map<UUID, Long> result = new HashMap<>();
        File[] files = folder.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return result;
        for (File f : files) {
            try {
                UUID uuid = UUID.fromString(f.getName().replace(".yml", ""));
                result.put(uuid, YamlConfiguration.loadConfiguration(f).getLong("playtime", 0L));
            } catch (IllegalArgumentException ignored) {}
        }
        return result;
    }

    @Override
    public void save(UUID uuid, long ms) {
        File file = fileFor(uuid);
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("playtime", ms);
        try { cfg.save(file); }
        catch (IOException e) { throw new RuntimeException("Failed to save playtime for " + uuid, e); }
    }

    @Override
    public void close() {} // nothing to close

    private File fileFor(UUID uuid) {
        return new File(folder, uuid + ".yml");
    }
}