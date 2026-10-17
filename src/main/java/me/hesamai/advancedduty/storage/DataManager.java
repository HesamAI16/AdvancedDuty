package me.hesamai.advancedduty.storage;

import me.hesamai.advancedduty.Main;
import me.hesamai.advancedduty.duty.DutyStateSnapshot;
import me.hesamai.advancedduty.duty.history.HistoryStorage;
import me.hesamai.advancedduty.duty.history.sql.SqlHistoryStorage;
import me.hesamai.advancedduty.duty.history.yaml.YamlHistoryStorage;
import me.hesamai.advancedduty.duty.inventory.storage.InventoryDataStorage;
import me.hesamai.advancedduty.duty.inventory.storage.sql.SqlInventoryStorage;
import me.hesamai.advancedduty.duty.inventory.storage.yaml.YamlInventoryStorage;
import me.hesamai.advancedduty.duty.playtime.PlayerStats;
import me.hesamai.advancedduty.duty.playtime.StatsDelta;
import me.hesamai.advancedduty.duty.playtime.StatsStorage;
import me.hesamai.advancedduty.duty.playtime.sql.SqlStatsStorage;
import me.hesamai.advancedduty.duty.playtime.yaml.YamlStatsStorage;
import me.hesamai.advancedduty.storage.sql.SqlDatabase;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Owns every persistence backend and the single storage thread. If the configured SQL backend cannot
 * be reached the plugin keeps working on YAML files (and says so loudly) instead of failing to start.
 */
public final class DataManager {

    private final Main plugin;
    private final StorageExecutor executor;
    private SqlDatabase database;
    private StorageType activeType = StorageType.YAML;

    private InventoryDataStorage inventories;
    private StatsStorage stats;
    private HistoryStorage history;

    public DataManager(Main plugin) {
        this.plugin = plugin;
        this.executor = new StorageExecutor(plugin.getLogger());
    }

    public void init() {
        StorageType wanted = StorageType.fromString(plugin.getConfig().getString("storage.type", "YAML"));

        if (wanted.isSql()) {
            try {
                database = SqlDatabase.open(plugin, wanted);
                SqlInventoryStorage inv = new SqlInventoryStorage(plugin, database);
                inv.initialize();
                inventories = inv;
                stats = new SqlStatsStorage(database);
                history = new SqlHistoryStorage(database);
                activeType = wanted;
                plugin.getLogger().info("Storage: " + wanted + " connected.");
            } catch (Throwable ex) {
                plugin.getLogger().severe("[Storage] " + wanted + " is unavailable! Falling back to YAML files.");
                plugin.getLogger().severe("[Storage] Reason: " + ex.getMessage());
                closeDatabaseQuietly();
                inventories = null;
                stats = null;
                history = null;
                activeType = StorageType.YAML;
            }
        }

        if (activeType == StorageType.YAML) {
            YamlInventoryStorage inv = new YamlInventoryStorage(plugin);
            inv.initialize();
            inventories = inv;
            stats = new YamlStatsStorage(plugin);
            history = new YamlHistoryStorage(plugin);
            plugin.getLogger().info("Storage: YAML files.");
        } else {
            migrateYamlToSql();
        }
    }

    // ------------------------------------------------------------------ one-time YAML -> SQL import

    private void migrateYamlToSql() {
        File dataFolder = new File(plugin.getDataFolder(), plugin.getConfig().getString("storage.yaml.folder", "data"));
        File playtimeFolder = new File(plugin.getDataFolder(), "data/playtimes");
        File[] invFiles = dataFolder.listFiles((d, n) -> n.endsWith(".yml") && isUuid(n.substring(0, n.length() - 4)));
        File[] ptFiles = playtimeFolder.listFiles((d, n) -> n.endsWith(".yml") && isUuid(n.substring(0, n.length() - 4)));
        int invCount = invFiles == null ? 0 : invFiles.length;
        int ptCount = ptFiles == null ? 0 : ptFiles.length;
        if (invCount == 0 && ptCount == 0) return;

        plugin.getLogger().info("[Migration] Found YAML data (" + invCount + " inventory, " + ptCount
                + " playtime file(s)). Importing into " + activeType + "...");

        File backup = new File(dataFolder, "_migrated_to_" + activeType.name().toLowerCase());
        if (!backup.exists() && !backup.mkdirs()) {
            plugin.getLogger().warning("[Migration] Cannot create " + backup + " - skipping migration.");
            return;
        }
        File backupPlaytimes = new File(backup, "playtimes");
        backupPlaytimes.mkdirs();

        int migratedInv = 0;
        int migratedPt = 0;

        if (invFiles != null) {
            YamlInventoryStorage yaml = new YamlInventoryStorage(plugin);
            yaml.initialize();
            for (File f : invFiles) {
                UUID uuid = UUID.fromString(f.getName().substring(0, f.getName().length() - 4));
                try {
                    if (inventories.load(uuid) == null) {
                        DutyStateSnapshot snap = yaml.load(uuid);
                        if (snap != null) inventories.save(uuid, snap);
                    }
                    move(f, new File(backup, f.getName()));
                    migratedInv++;
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.WARNING, "[Migration] Could not migrate " + f.getName()
                            + " (left untouched): " + ex.getMessage());
                }
            }
        }

        if (ptFiles != null) {
            YamlStatsStorage yaml = new YamlStatsStorage(plugin);
            for (File f : ptFiles) {
                UUID uuid = UUID.fromString(f.getName().substring(0, f.getName().length() - 4));
                try {
                    if (stats.load(uuid) == null) {
                        PlayerStats old = yaml.load(uuid);
                        if (old != null) {
                            stats.apply(new StatsDelta(uuid, old.getName(), false, old.getTotal(), old.getSessions(),
                                    old.getLongest(), old.getLastEnd(), old.dailySnapshot(), old.claimsSnapshot()));
                        }
                    }
                    move(f, new File(backupPlaytimes, f.getName()));
                    migratedPt++;
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.WARNING, "[Migration] Could not migrate " + f.getName()
                            + " (left untouched): " + ex.getMessage());
                }
            }
        }

        plugin.getLogger().info("[Migration] Done: " + migratedInv + " inventory and " + migratedPt
                + " playtime record(s) imported. Originals moved to " + backup.getName() + "/.");
    }

    private static boolean isUuid(String s) {
        try {
            UUID.fromString(s);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static void move(File from, File to) throws java.io.IOException {
        Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    // ------------------------------------------------------------------ accessors

    public StorageExecutor executor() { return executor; }
    public InventoryDataStorage inventories() { return inventories; }
    public StatsStorage stats() { return stats; }
    public HistoryStorage history() { return history; }
    public SqlDatabase database() { return database; }
    public StorageType activeType() { return activeType; }
    public boolean isSql() { return activeType.isSql(); }

    // ------------------------------------------------------------------ shutdown

    /** Flushes the write queue, then releases the pools. Must be the last thing onDisable does. */
    public void shutdown() {
        executor.shutdown(60);
        try {
            if (inventories != null) inventories.shutdown();
            if (stats != null) stats.close();
            if (history != null) history.close();
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.WARNING, "Error while closing storage", ex);
        }
        closeDatabaseQuietly();
    }

    private void closeDatabaseQuietly() {
        if (database != null) {
            try {
                database.close();
            } catch (Throwable ignored) {
                // pool already closed
            }
            database = null;
        }
    }

    /** Placeholder to keep API symmetrical for tests. */
    public java.util.Set<UUID> noPlayers() {
        return Collections.emptySet();
    }
}
