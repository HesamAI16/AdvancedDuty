package me.hesamai.advancedduty.duty.migration;

import me.hesamai.advancedduty.Main;
import me.hesamai.advancedduty.duty.DutyStateSnapshot;
import me.hesamai.advancedduty.duty.inventory.storage.mysql.MySqlInventoryStorage;
import me.hesamai.advancedduty.duty.inventory.storage.yaml.YamlInventoryStorage;
import me.hesamai.advancedduty.duty.playtime.PlaytimeStorage;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.UUID;

public class StorageMigrator {

    private final Main plugin;

    public StorageMigrator(Main plugin) {
        this.plugin = plugin;
    }

    public void migrateYamlToMysqlIfNeeded(MySqlInventoryStorage mysqlInventory,
                                           PlaytimeStorage mysqlPlaytime) {
        File dataFolder = new File(plugin.getDataFolder(), "data");
        File playtimeFolder = new File(plugin.getDataFolder(), "data/playtimes");

        boolean hasInventoryData = hasYamlFiles(dataFolder);
        boolean hasPlaytimeData  = hasYamlFiles(playtimeFolder);

        if (!hasInventoryData && !hasPlaytimeData) return;

        plugin.getLogger().info("[AdvancedDuty] Detected YAML data. starting migration to MySQL...");

        int inventoryCount = 0;
        int playtimeCount  = 0;

        if (hasInventoryData) {
            YamlInventoryStorage yamlInventory = new YamlInventoryStorage(plugin);
            yamlInventory.initialize();

            File[] files = dataFolder.listFiles((d, n) -> n.endsWith(".yml"));
            if (files != null) {
                for (File f : files) {
                    String name = f.getName().replace(".yml", "");
                    try {
                        UUID uuid = UUID.fromString(name);
                        DutyStateSnapshot snapshot = yamlInventory.load(uuid);
                        mysqlInventory.save(uuid, snapshot);
                        f.delete();
                        inventoryCount++;
                    } catch (IllegalArgumentException ignored) {
                    } catch (Exception ex) {
                        plugin.getLogger().warning("[Migration] Failed to migrate inventory for " + name + ": " + ex.getMessage());
                    }
                }
            }
        }

        if (hasPlaytimeData) {
            File[] files = playtimeFolder.listFiles((d, n) -> n.endsWith(".yml"));
            if (files != null) {
                for (File f : files) {
                    String name = f.getName().replace(".yml", "");
                    try {
                        UUID uuid = UUID.fromString(name);
                        long ms = YamlConfiguration.loadConfiguration(f).getLong("playtime", 0L);
                        mysqlPlaytime.save(uuid, ms);
                        f.delete();
                        playtimeCount++;
                    } catch (IllegalArgumentException ignored) {
                    } catch (Exception ex) {
                        plugin.getLogger().warning("[Migration] Failed to migrate playtime for " + name + ": " + ex.getMessage());
                    }
                }
            }

            if (playtimeFolder.exists() && playtimeFolder.list() != null
                    && playtimeFolder.list().length == 0) {
                playtimeFolder.delete();
            }
        }

        plugin.getLogger().info("[AdvancedDuty] Migration complete | "
                + inventoryCount + " inventory record(s), "
                + playtimeCount  + " playtime record(s) migrated.");
    }

    private boolean hasYamlFiles(File folder) {
        if (!folder.exists()) return false;
        File[] files = folder.listFiles((d, n) -> n.endsWith(".yml"));
        return files != null && files.length > 0;
    }
}