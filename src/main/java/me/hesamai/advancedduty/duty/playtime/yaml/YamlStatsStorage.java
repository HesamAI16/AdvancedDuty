package me.hesamai.advancedduty.duty.playtime.yaml;

import me.hesamai.advancedduty.Main;
import me.hesamai.advancedduty.duty.playtime.PlayerStats;
import me.hesamai.advancedduty.duty.playtime.StatsDelta;
import me.hesamai.advancedduty.duty.playtime.StatsStorage;
import me.hesamai.advancedduty.storage.yaml.AtomicFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** One small YAML file per player: {@code data/playtimes/<uuid>.yml}. Old files (only "playtime") stay valid. */
public class YamlStatsStorage implements StatsStorage {

    private final File folder;
    private final Main plugin;

    public YamlStatsStorage(Main plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "data/playtimes");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create " + folder);
        }
    }

    private File fileFor(UUID uuid) {
        return new File(folder, uuid + ".yml");
    }

    @Override
    public PlayerStats load(UUID uuid) throws Exception {
        File file = fileFor(uuid);
        if (!file.exists()) return null;
        YamlConfiguration cfg = loadOrQuarantine(file);
        return cfg == null ? null : read(uuid, cfg);
    }

    /** Reads a player file; a corrupt file is moved aside (never overwritten) and treated as absent. */
    private YamlConfiguration loadOrQuarantine(File file) {
        try {
            return AtomicFiles.loadYaml(file);
        } catch (java.io.IOException ex) {
            File moved = AtomicFiles.quarantine(file);
            plugin.getLogger().warning("[Playtime] " + ex.getMessage() + " - moved to "
                    + (moved == null ? "(rename failed)" : moved.getName()));
            return null;
        }
    }

    private PlayerStats read(UUID uuid, YamlConfiguration cfg) {
        Map<String, Long> daily = new TreeMap<>();
        ConfigurationSection ds = cfg.getConfigurationSection("daily");
        if (ds != null) {
            for (String k : ds.getKeys(false)) daily.put(k, ds.getLong(k));
        }
        return PlayerStats.loaded(uuid,
                cfg.getString("name"),
                cfg.getLong("playtime", 0L),
                cfg.getInt("sessions", 0),
                cfg.getLong("longest", 0L),
                cfg.getLong("last-end", 0L),
                daily,
                new LinkedHashSet<>(cfg.getStringList("claims")));
    }

    @Override
    public void apply(StatsDelta d) throws Exception {
        File file = fileFor(d.getUuid());
        YamlConfiguration cfg = file.exists() ? loadOrQuarantine(file) : null;
        if (cfg == null) cfg = new YamlConfiguration();

        if (d.isReset()) {
            cfg.set("playtime", 0L);
            cfg.set("sessions", 0);
            cfg.set("longest", 0L);
            cfg.set("last-end", 0L);
            cfg.set("daily", null);
        }

        cfg.set("playtime", cfg.getLong("playtime", 0L) + d.getTotal());
        cfg.set("sessions", cfg.getInt("sessions", 0) + d.getSessions());
        cfg.set("longest", Math.max(cfg.getLong("longest", 0L), d.getLongest()));
        cfg.set("last-end", Math.max(cfg.getLong("last-end", 0L), d.getLastEnd()));
        if (d.getName() != null) cfg.set("name", d.getName());

        for (Map.Entry<String, Long> e : d.getDaily().entrySet()) {
            String path = "daily." + e.getKey();
            cfg.set(path, cfg.getLong(path, 0L) + e.getValue());
        }

        if (!d.getClaims().isEmpty()) {
            Set<String> claims = new LinkedHashSet<>(cfg.getStringList("claims"));
            claims.addAll(d.getClaims());
            cfg.set("claims", new java.util.ArrayList<>(claims));
        }

        AtomicFiles.saveYaml(cfg, file, false);
    }

    @Override
    public Map<UUID, PlayerStats> loadAll(String sinceDayKey) {
        Map<UUID, PlayerStats> result = new HashMap<>();
        File[] files = folder.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return result;
        for (File f : files) {
            try {
                UUID uuid = UUID.fromString(f.getName().substring(0, f.getName().length() - 4));
                PlayerStats stats = read(uuid, YamlConfiguration.loadConfiguration(f));
                result.put(uuid, stats);
            } catch (IllegalArgumentException ignored) {
                // not a player file
            }
        }
        return result;
    }

    @Override
    public void prune(String beforeDayKey) throws Exception {
        File[] files = folder.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return;
        for (File f : files) {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
            ConfigurationSection ds = cfg.getConfigurationSection("daily");
            if (ds == null) continue;
            boolean changed = false;
            for (String k : new java.util.ArrayList<>(ds.getKeys(false))) {
                if (k.compareTo(beforeDayKey) < 0) {
                    cfg.set("daily." + k, null);
                    changed = true;
                }
            }
            if (changed) AtomicFiles.saveYaml(cfg, f, false);
        }
    }

    @Override
    public void close() {
        // nothing to close
    }
}
