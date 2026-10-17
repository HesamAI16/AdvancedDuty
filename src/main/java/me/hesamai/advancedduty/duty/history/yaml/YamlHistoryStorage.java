package me.hesamai.advancedduty.duty.history.yaml;

import me.hesamai.advancedduty.Main;
import me.hesamai.advancedduty.duty.history.HistoryStorage;
import me.hesamai.advancedduty.duty.history.SessionRecord;
import me.hesamai.advancedduty.storage.yaml.AtomicFiles;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** {@code data/history/<uuid>.yml}: a capped list of finished sessions per player. */
public class YamlHistoryStorage implements HistoryStorage {

    private final File folder;
    private final Main plugin;

    public YamlHistoryStorage(Main plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "data/history");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create " + folder);
        }
    }

    private File fileFor(UUID uuid) {
        return new File(folder, uuid + ".yml");
    }

    @Override
    public void append(SessionRecord r, int maxEntries) throws IOException {
        File file = fileFor(r.getUuid());
        YamlConfiguration cfg = new YamlConfiguration();
        if (file.exists()) {
            try {
                cfg = AtomicFiles.loadYaml(file);
            } catch (IOException ex) {
                AtomicFiles.quarantine(file);
                cfg = new YamlConfiguration();
            }
        }
        List<Map<?, ?>> list = new ArrayList<>(cfg.getMapList("sessions"));
        java.util.LinkedHashMap<String, Object> entry = new java.util.LinkedHashMap<>();
        entry.put("start", r.getStartMs());
        entry.put("end", r.getEndMs());
        entry.put("active", r.getActiveMs());
        entry.put("reason", r.getReason() == null ? "" : r.getReason());
        entry.put("cause", r.getEndCause() == null ? "" : r.getEndCause());
        list.add(entry);
        while (maxEntries > 0 && list.size() > maxEntries) list.remove(0);
        cfg.set("name", r.getName());
        cfg.set("sessions", list);
        AtomicFiles.saveYaml(cfg, file, false);
    }

    @Override
    public List<SessionRecord> load(UUID uuid, int limit, int offset) {
        List<SessionRecord> all = readAll(uuid);
        // stored oldest -> newest; return newest first
        List<SessionRecord> out = new ArrayList<>();
        for (int i = all.size() - 1 - offset; i >= 0 && out.size() < limit; i--) out.add(all.get(i));
        return out;
    }

    @Override
    public int count(UUID uuid) {
        return readAll(uuid).size();
    }

    @Override
    public List<SessionRecord> loadRecent(int limit) {
        List<SessionRecord> merged = new ArrayList<>();
        File[] files = folder.listFiles((d, n) -> n.endsWith(".yml"));
        if (files != null) {
            for (File f : files) {
                try {
                    merged.addAll(readAll(UUID.fromString(f.getName().substring(0, f.getName().length() - 4))));
                } catch (IllegalArgumentException ignored) {
                    // foreign file
                }
            }
        }
        merged.sort(Comparator.comparingLong(SessionRecord::getEndMs).reversed());
        return merged.size() > limit ? new ArrayList<>(merged.subList(0, limit)) : merged;
    }

    private List<SessionRecord> readAll(UUID uuid) {
        File file = fileFor(uuid);
        List<SessionRecord> out = new ArrayList<>();
        if (!file.exists()) return out;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        String name = cfg.getString("name");
        for (Map<?, ?> m : cfg.getMapList("sessions")) {
            out.add(new SessionRecord(uuid, name,
                    num(m.get("start")), num(m.get("end")), num(m.get("active")),
                    str(m.get("reason")), str(m.get("cause"))));
        }
        return out;
    }

    private static long num(Object o) {
        return o instanceof Number ? ((Number) o).longValue() : 0L;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    @Override
    public void close() {
        // nothing to close
    }
}
