package me.hesamai.advancedduty.duty.playtime.mysql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.hesamai.advancedduty.Main;
import me.hesamai.advancedduty.duty.playtime.PlaytimeStorage;
import org.bukkit.configuration.ConfigurationSection;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MySqlPlaytimeStorage implements PlaytimeStorage {

    private final HikariDataSource ds;
    private final String table;

    public MySqlPlaytimeStorage(Main plugin) {
        ConfigurationSection mysql = plugin.getConfig().getConfigurationSection("storage.mysql");

        table = mysql.getString("playtime-table", "duty_playtime");

        HikariConfig hk = new HikariConfig();
        hk.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s?useSSL=%b&autoReconnect=true",
                mysql.getString("host", "localhost"),
                mysql.getInt("port", 3306),
                mysql.getString("database", "advancedduty"),
                mysql.getBoolean("use-ssl", false)));
        hk.setUsername(mysql.getString("username", "root"));
        hk.setPassword(mysql.getString("password", ""));
        hk.setMaximumPoolSize(mysql.getInt("pool-size", 5));
        hk.setConnectionTimeout(mysql.getLong("connection-timeout-ms", 5000));
        hk.setMaxLifetime(mysql.getLong("max-lifetime-ms", 1800000));
        hk.setPoolName("AdvancedDuty-Playtime");

        ds = new HikariDataSource(hk);
        createTable();
    }

    private void createTable() {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS `" + table + "` (" +
                            "  `uuid` VARCHAR(36) NOT NULL PRIMARY KEY," +
                            "  `playtime_ms` BIGINT NOT NULL DEFAULT 0," +
                            "  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                            "    ON UPDATE CURRENT_TIMESTAMP" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;"
            );
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create playtime table", e);
        }
    }

    @Override
    public long load(UUID uuid) {
        String sql = "SELECT `playtime_ms` FROM `" + table + "` WHERE `uuid` = ?";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load playtime for " + uuid, e);
        }
    }

    @Override
    public Map<UUID, Long> loadAll() {
        Map<UUID, Long> result = new HashMap<>();
        String sql = "SELECT `uuid`, `playtime_ms` FROM `" + table + "`";
        try (Connection c = ds.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                try {
                    result.put(UUID.fromString(rs.getString(1)), rs.getLong(2));
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load all playtimes", e);
        }
        return result;
    }

    @Override
    public void save(UUID uuid, long ms) {
        String sql =
                "INSERT INTO `" + table + "` (`uuid`, `playtime_ms`) VALUES (?, ?)" +
                        " ON DUPLICATE KEY UPDATE `playtime_ms` = ?";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, ms);
            ps.setLong(3, ms);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save playtime for " + uuid, e);
        }
    }

    @Override
    public void close() {
        if (ds != null && !ds.isClosed()) ds.close();
    }
}