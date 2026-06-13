package me.hesamai.advancedduty.duty.inventory.storage.mysql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.hesamai.advancedduty.Main;
import me.hesamai.advancedduty.duty.DutyMode;
import me.hesamai.advancedduty.duty.DutySession;
import me.hesamai.advancedduty.duty.DutyStateSnapshot;
import me.hesamai.advancedduty.duty.PlayerState;
import me.hesamai.advancedduty.duty.inventory.DualInventoryProfile;
import me.hesamai.advancedduty.duty.inventory.InventorySerializer;
import me.hesamai.advancedduty.duty.inventory.PlayerInventoryState;
import me.hesamai.advancedduty.duty.inventory.storage.InventoryDataStorage;
import org.bukkit.configuration.file.YamlConfiguration;

import java.sql.*;
import java.util.List;
import java.util.UUID;

public class MySqlInventoryStorage implements InventoryDataStorage {

    private final Main plugin;
    private HikariDataSource dataSource;
    private String table;

    public MySqlInventoryStorage(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public void initialize() {
        String host     = plugin.getConfig().getString("storage.mysql.host", "localhost");
        int    port     = plugin.getConfig().getInt("storage.mysql.port", 3306);
        String database = plugin.getConfig().getString("storage.mysql.database", "advancedduty");
        String username = plugin.getConfig().getString("storage.mysql.username", "root");
        String password = plugin.getConfig().getString("storage.mysql.password", "");
        boolean useSsl  = plugin.getConfig().getBoolean("storage.mysql.use-ssl", false);
        int poolSize    = plugin.getConfig().getInt("storage.mysql.pool-size", 5);

        this.table = plugin.getConfig().getString("storage.mysql.table", "duty_inventories");

        HikariConfig hk = new HikariConfig();
        hk.setJdbcUrl(
                "jdbc:mysql://" + host + ":" + port + "/" + database
                        + "?useSSL=" + useSsl
                        + "&autoReconnect=true"
                        + "&characterEncoding=utf8"
        );
        hk.setUsername(username);
        hk.setPassword(password);
        hk.setMaximumPoolSize(poolSize);
        hk.setMinimumIdle(1);
        hk.setConnectionTimeout(5000);
        hk.setIdleTimeout(300_000);
        hk.setMaxLifetime(600_000);
        hk.setPoolName("AdvancedDuty-Inventory");

        hk.addDataSourceProperty("cachePrepStmts", "true");
        hk.addDataSourceProperty("prepStmtCacheSize", "250");
        hk.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hk.addDataSourceProperty("useServerPrepStmts", "true");

        dataSource = new HikariDataSource(hk);

        createTable();
        runMigrations();
    }

    private void createTable() {
        String sql =
                "CREATE TABLE IF NOT EXISTS `" + table + "` (" +
                        "  `uuid`                    VARCHAR(36)  NOT NULL PRIMARY KEY," +
                        "  `off_contents`            LONGTEXT," +
                        "  `off_armor`               LONGTEXT," +
                        "  `off_offhand`             LONGTEXT," +
                        "  `off_enderchest`          LONGTEXT," +
                        "  `on_contents`             LONGTEXT," +
                        "  `on_armor`                LONGTEXT," +
                        "  `on_offhand`              LONGTEXT," +
                        "  `on_enderchest`           LONGTEXT," +
                        "  `off_state`               LONGTEXT," +
                        "  `on_state`                LONGTEXT," +
                        "  `stored_staff_groups`     LONGTEXT," +
                        "  `stored_staff_permissions`LONGTEXT," +
                        "  `session_data`            LONGTEXT," +
                        "  `playtime`                BIGINT       NOT NULL DEFAULT 0," +
                        "  `updated_at`              BIGINT" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        try (Connection c = dataSource.getConnection();
             Statement  s = c.createStatement()) {
            s.executeUpdate(sql);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to create inventory table", ex);
        }
    }

    private void runMigrations() {
        addColumnIfMissing("off_state",   "LONGTEXT");
        addColumnIfMissing("on_state",    "LONGTEXT");
        addColumnIfMissing("playtime",    "BIGINT NOT NULL DEFAULT 0");
    }

    private void addColumnIfMissing(String column, String definition) {
        try (Connection c = dataSource.getConnection();
             Statement  s = c.createStatement()) {
            s.executeUpdate("ALTER TABLE `" + table + "` ADD COLUMN `" + column + "` " + definition);
        } catch (SQLException ignored) {
        }
    }

    @Override
    public DutyStateSnapshot load(UUID uuid) {
        DualInventoryProfile profile = new DualInventoryProfile(uuid);
        DutySession session = null;
        long playtime = 0L;

        String sql = "SELECT * FROM `" + table + "` WHERE `uuid` = ?";

        try (Connection        c  = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return new DutyStateSnapshot(profile, null, DutyMode.OFF_DUTY, 0L);
                }

                profile.setOffDutyInventory(readInventoryState(
                        rs.getString("off_contents"),
                        rs.getString("off_armor"),
                        rs.getString("off_offhand"),
                        rs.getString("off_enderchest")
                ));

                profile.setOnDutyInventory(readInventoryState(
                        rs.getString("on_contents"),
                        rs.getString("on_armor"),
                        rs.getString("on_offhand"),
                        rs.getString("on_enderchest")
                ));

                profile.setOffDutyState(deserializeState(rs.getString("off_state")));
                profile.setOnDutyState(deserializeState(rs.getString("on_state")));

                profile.setStoredStaffGroups(deserializeStringList(rs.getString("stored_staff_groups")));
                profile.setStoredStaffPermissions(deserializeStringList(rs.getString("stored_staff_permissions")));

                playtime = rs.getLong("playtime");

                String sessionRaw = rs.getString("session_data");
                if (sessionRaw != null && !sessionRaw.trim().isEmpty()) {
                    try {
                        YamlConfiguration cfg = new YamlConfiguration();
                        cfg.loadFromString(sessionRaw);
                        session = DutySession.readFrom(cfg);
                    } catch (Exception ex) {
                        plugin.getLogger().warning("Failed to parse session_data for " + uuid + ": " + ex.getMessage());
                    }
                }
            }

        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load inventory profile for " + uuid, ex);
        }

        DutyMode mode = (session != null) ? DutyMode.ON_DUTY : DutyMode.OFF_DUTY;
        return new DutyStateSnapshot(profile, session, mode, playtime);
    }

    @Override
    public void save(UUID uuid, DutyStateSnapshot snapshot) {
        String sql =
                "INSERT INTO `" + table + "` (" +
                        "  `uuid`, `off_contents`, `off_armor`, `off_offhand`, `off_enderchest`," +
                        "  `on_contents`, `on_armor`, `on_offhand`, `on_enderchest`," +
                        "  `off_state`, `on_state`," +
                        "  `stored_staff_groups`, `stored_staff_permissions`," +
                        "  `session_data`, `playtime`, `updated_at`" +
                        ") VALUES (?,?,?,?,?, ?,?,?,?, ?,?, ?,?, ?,?,?)" +
                        " ON DUPLICATE KEY UPDATE" +
                        "  `off_contents`             = VALUES(`off_contents`)," +
                        "  `off_armor`                = VALUES(`off_armor`)," +
                        "  `off_offhand`              = VALUES(`off_offhand`)," +
                        "  `off_enderchest`           = VALUES(`off_enderchest`)," +
                        "  `on_contents`              = VALUES(`on_contents`)," +
                        "  `on_armor`                 = VALUES(`on_armor`)," +
                        "  `on_offhand`               = VALUES(`on_offhand`)," +
                        "  `on_enderchest`            = VALUES(`on_enderchest`)," +
                        "  `off_state`                = VALUES(`off_state`)," +
                        "  `on_state`                 = VALUES(`on_state`)," +
                        "  `stored_staff_groups`      = VALUES(`stored_staff_groups`)," +
                        "  `stored_staff_permissions` = VALUES(`stored_staff_permissions`)," +
                        "  `session_data`             = VALUES(`session_data`)," +
                        "  `playtime`                 = VALUES(`playtime`)," +
                        "  `updated_at`               = VALUES(`updated_at`)";

        try (Connection        c  = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            DualInventoryProfile profile = snapshot.getProfile();
            PlayerInventoryState off = profile == null ? null : profile.getOffDutyInventory();
            PlayerInventoryState on  = profile == null ? null : profile.getOnDutyInventory();

            ps.setString(1,  uuid.toString());
            ps.setString(2,  off == null ? "" : InventorySerializer.itemStackArrayToBase64(off.getContents()));
            ps.setString(3,  off == null ? "" : InventorySerializer.itemStackArrayToBase64(off.getArmorContents()));
            ps.setString(4,  off == null ? "" : InventorySerializer.itemStackToBase64(off.getOffHand()));
            ps.setString(5,  off == null ? "" : InventorySerializer.itemStackArrayToBase64(off.getEnderChestContents()));
            ps.setString(6,  on  == null ? "" : InventorySerializer.itemStackArrayToBase64(on.getContents()));
            ps.setString(7,  on  == null ? "" : InventorySerializer.itemStackArrayToBase64(on.getArmorContents()));
            ps.setString(8,  on  == null ? "" : InventorySerializer.itemStackToBase64(on.getOffHand()));
            ps.setString(9,  on  == null ? "" : InventorySerializer.itemStackArrayToBase64(on.getEnderChestContents()));
            ps.setString(10, serializeState(profile == null ? null : profile.getOffDutyState()));
            ps.setString(11, serializeState(profile == null ? null : profile.getOnDutyState()));
            ps.setString(12, serializeStringList(profile == null ? null : profile.getStoredStaffGroups()));
            ps.setString(13, serializeStringList(profile == null ? null : profile.getStoredStaffPermissions()));
            ps.setString(14, serializeSession(snapshot.getSession()));
            ps.setLong(15,   snapshot.getTotalPlaytimeMs());
            ps.setLong(16,   System.currentTimeMillis());

            ps.executeUpdate();

        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save inventory profile for " + uuid, ex);
        }
    }

    @Override
    public void delete(UUID uuid) {
        try (Connection        c  = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM `" + table + "` WHERE `uuid` = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to delete inventory profile for " + uuid, ex);
        }
    }

    @Override
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    private PlayerInventoryState readInventoryState(String contents, String armor,
                                                    String offHand, String enderChest) {
        if (isBlank(contents) && isBlank(armor) && isBlank(offHand) && isBlank(enderChest)) {
            return null;
        }
        return new PlayerInventoryState(
                InventorySerializer.itemStackArrayFromBase64(contents),
                InventorySerializer.itemStackArrayFromBase64(armor),
                InventorySerializer.itemStackFromBase64(offHand),
                InventorySerializer.itemStackArrayFromBase64(enderChest)
        );
    }

    private PlayerState deserializeState(String raw) {
        if (isBlank(raw)) return null;
        try {
            YamlConfiguration cfg = new YamlConfiguration();
            cfg.loadFromString(raw);
            return PlayerState.read(cfg);
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to deserialize player state: " + ex.getMessage());
            return null;
        }
    }

    private String serializeState(PlayerState state) {
        if (state == null) return "";
        YamlConfiguration cfg = new YamlConfiguration();
        state.write(cfg);
        return cfg.saveToString();
    }

    private String serializeSession(DutySession session) {
        if (session == null) return "";
        YamlConfiguration cfg = new YamlConfiguration();
        session.writeTo(cfg);
        return cfg.saveToString();
    }

    private String serializeStringList(List<String> list) {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("list", list);
        return cfg.saveToString();
    }

    private List<String> deserializeStringList(String raw) {
        YamlConfiguration cfg = new YamlConfiguration();
        if (!isBlank(raw)) {
            try {
                cfg.loadFromString(raw);
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to parse stored string list: " + ex.getMessage());
            }
        }
        return cfg.getStringList("list");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isEmpty();
    }
}