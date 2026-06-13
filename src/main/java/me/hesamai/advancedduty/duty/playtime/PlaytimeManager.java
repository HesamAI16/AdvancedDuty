package me.hesamai.advancedduty.duty.playtime;

import me.hesamai.advancedduty.Main;
import me.hesamai.advancedduty.duty.playtime.mysql.MySqlPlaytimeStorage;
import me.hesamai.advancedduty.duty.playtime.yaml.YamlPlaytimeStorage;
import org.bukkit.Bukkit;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlaytimeManager {

    private final Main plugin;
    private final PlaytimeStorage storage;

    private final Map<UUID, Long> sessionStart = new ConcurrentHashMap<>();
    private final Map<UUID, Long> totalPlaytime = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pausedAt = new ConcurrentHashMap<>();

    public PlaytimeManager(Main plugin) {
        this.plugin = plugin;

        boolean proxyEnabled = plugin.getConfig().getBoolean("proxy-mode.enabled", false);
        boolean sharedPlaytime = plugin.getConfig().getBoolean("proxy-mode.shared-playtime", true);
        String storageType = plugin.getConfig().getString("storage.type", "YAML");

        if (proxyEnabled && sharedPlaytime) {
            // proxy network → shared database
            this.storage = new MySqlPlaytimeStorage(plugin);
            plugin.getLogger().info("Proxy mode enabled: using MySQL for shared playtime.");
        } else if (storageType.equalsIgnoreCase("MYSQL")) {
            this.storage = new MySqlPlaytimeStorage(plugin);
            plugin.getLogger().info("Using MySQL playtime storage.");
        } else {
            this.storage = new YamlPlaytimeStorage(plugin);
            plugin.getLogger().info("Using YAML playtime storage.");
        }
    }

    public void load(UUID uuid) {
        if (!totalPlaytime.containsKey(uuid)) {
            totalPlaytime.put(uuid, storage.load(uuid));
        }
    }

    public void startSession(UUID uuid) {
        load(uuid);
        sessionStart.put(uuid, System.currentTimeMillis());
    }

    public long endSession(UUID uuid) {
        Long start = sessionStart.remove(uuid);
        pausedAt.remove(uuid);
        if (start == null) return 0L;
        long elapsed = System.currentTimeMillis() - start;
        totalPlaytime.merge(uuid, elapsed, Long::sum);
        saveAsync(uuid);
        return elapsed;
    }

    public long getTotalPlaytime(UUID uuid) {
        long stored = totalPlaytime.getOrDefault(uuid, 0L);
        Long start = sessionStart.get(uuid);
        if (start != null) stored += System.currentTimeMillis() - start;
        return stored;
    }

    public void setStoredPlaytime(UUID uuid, long ms) {
        totalPlaytime.merge(uuid, ms, Math::max);
    }

    public void saveAsync(UUID uuid) {
        long total = getTotalPlaytime(uuid);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> storage.save(uuid, total));
    }

    public void saveSync(UUID uuid) {
        storage.save(uuid, getTotalPlaytime(uuid));
    }

    public void unload(UUID uuid) {
        sessionStart.remove(uuid);
        pausedAt.remove(uuid);
        totalPlaytime.remove(uuid);
    }

    public void pauseSession(UUID uuid) {
        if (!sessionStart.containsKey(uuid) || pausedAt.containsKey(uuid)) return;
        pausedAt.put(uuid, System.currentTimeMillis());
    }

    public void resumeSession(UUID uuid) {
        Long pauseTime = pausedAt.remove(uuid);
        if (pauseTime == null || !sessionStart.containsKey(uuid)) return;
        long pausedDuration = System.currentTimeMillis() - pauseTime;
        sessionStart.computeIfPresent(uuid, (k, start) -> start + pausedDuration);
    }

    public void resetPlaytime(UUID uuid) {
        totalPlaytime.put(uuid, 0L);
        sessionStart.remove(uuid);
        pausedAt.remove(uuid);
        saveAsync(uuid);
    }

    public boolean hasActiveSession(UUID uuid) {
        return sessionStart.containsKey(uuid);
    }

    public Map<UUID, Long> getAllStored() {
        Map<UUID, Long> all = new HashMap<>(storage.loadAll());
        totalPlaytime.forEach((uuid, ms) -> all.merge(uuid, ms, Math::max));
        return all;
    }

    public long getCurrentSessionDuration(UUID uuid) {
        Long start = sessionStart.get(uuid);
        return start != null ? System.currentTimeMillis() - start : 0L;
    }

    public PlaytimeStorage getStorage() {
        return storage;
    }
}