package me.hesamai.advancedduty.duty.playtime.sql;

import me.hesamai.advancedduty.duty.playtime.PlayerStats;
import me.hesamai.advancedduty.duty.playtime.StatsDelta;
import me.hesamai.advancedduty.duty.playtime.StatsStorage;
import me.hesamai.advancedduty.storage.sql.SqlDatabase;
import me.hesamai.advancedduty.storage.sql.SqlUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Playtime / statistics / milestone claims in three small tables, valid on MySQL, MariaDB and SQLite. */
public class SqlStatsStorage implements StatsStorage {

    private final SqlDatabase db;
    private final String main;
    private final String dailyTable;
    private final String claimsTable;

    public SqlStatsStorage(SqlDatabase db) throws SQLException {
        this.db = db;
        this.main = db.table("playtime-table", "duty_playtime");
        this.dailyTable = db.table("daily-table", "duty_playtime_daily");
        this.claimsTable = db.table("claims-table", "duty_claims");
        createTables();
    }

    private void createTables() throws SQLException {
        String suffix = db.tableSuffix();
        db.execute("CREATE TABLE IF NOT EXISTS `" + main + "` ("
                + " `uuid` VARCHAR(36) NOT NULL PRIMARY KEY,"
                + " `playtime_ms` BIGINT NOT NULL DEFAULT 0"
                + ")" + suffix);
        // columns added in 2.0.0 - the table may already exist from an older version
        db.addColumnIfMissing(main, "sessions", "INT NOT NULL DEFAULT 0");
        db.addColumnIfMissing(main, "longest_ms", "BIGINT NOT NULL DEFAULT 0");
        db.addColumnIfMissing(main, "last_end", "BIGINT NOT NULL DEFAULT 0");
        db.addColumnIfMissing(main, "name", "VARCHAR(32)");

        db.execute("CREATE TABLE IF NOT EXISTS `" + dailyTable + "` ("
                + " `uuid` VARCHAR(36) NOT NULL,"
                + " `day` VARCHAR(10) NOT NULL,"
                + " `ms` BIGINT NOT NULL DEFAULT 0,"
                + " PRIMARY KEY (`uuid`, `day`)"
                + ")" + suffix);

        db.execute("CREATE TABLE IF NOT EXISTS `" + claimsTable + "` ("
                + " `uuid` VARCHAR(36) NOT NULL,"
                + " `claim_key` VARCHAR(100) NOT NULL,"
                + " `claimed_at` BIGINT NOT NULL DEFAULT 0,"
                + " PRIMARY KEY (`uuid`, `claim_key`)"
                + ")" + suffix);
    }

    @Override
    public PlayerStats load(UUID uuid) throws Exception {
        try (Connection c = db.getConnection()) {
            String name;
            long total;
            int sessions;
            long longest;
            long lastEnd;
            try (PreparedStatement ps = SqlUtil.prepare(c,
                    "SELECT `playtime_ms`, `sessions`, `longest_ms`, `last_end`, `name` FROM `" + main + "` WHERE `uuid` = ?",
                    uuid.toString());
                 ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                total = rs.getLong(1);
                sessions = rs.getInt(2);
                longest = rs.getLong(3);
                lastEnd = rs.getLong(4);
                name = rs.getString(5);
            }

            Map<String, Long> daily = new TreeMap<>();
            try (PreparedStatement ps = SqlUtil.prepare(c,
                    "SELECT `day`, `ms` FROM `" + dailyTable + "` WHERE `uuid` = ?", uuid.toString());
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) daily.put(rs.getString(1), rs.getLong(2));
            }

            Set<String> claims = new LinkedHashSet<>();
            try (PreparedStatement ps = SqlUtil.prepare(c,
                    "SELECT `claim_key` FROM `" + claimsTable + "` WHERE `uuid` = ?", uuid.toString());
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) claims.add(rs.getString(1));
            }
            return PlayerStats.loaded(uuid, name, total, sessions, longest, lastEnd, daily, claims);
        }
    }

    @Override
    public void apply(StatsDelta d) throws Exception {
        String id = d.getUuid().toString();
        try (Connection c = db.getConnection()) {
            boolean oldAuto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                if (d.isReset()) {
                    SqlUtil.update(c, "UPDATE `" + main + "` SET `playtime_ms` = 0, `sessions` = 0,"
                            + " `longest_ms` = 0, `last_end` = 0 WHERE `uuid` = ?", id);
                    SqlUtil.update(c, "DELETE FROM `" + dailyTable + "` WHERE `uuid` = ?", id);
                }

                SqlUtil.upsert(c,
                        "UPDATE `" + main + "` SET `playtime_ms` = `playtime_ms` + ?, `sessions` = `sessions` + ?,"
                                + " `longest_ms` = CASE WHEN `longest_ms` < ? THEN ? ELSE `longest_ms` END,"
                                + " `last_end` = CASE WHEN `last_end` < ? THEN ? ELSE `last_end` END,"
                                + " `name` = COALESCE(?, `name`) WHERE `uuid` = ?",
                        new Object[]{d.getTotal(), d.getSessions(), d.getLongest(), d.getLongest(),
                                d.getLastEnd(), d.getLastEnd(), d.getName(), id},
                        "INSERT INTO `" + main + "` (`uuid`, `playtime_ms`, `sessions`, `longest_ms`, `last_end`, `name`)"
                                + " VALUES (?, ?, ?, ?, ?, ?)",
                        new Object[]{id, d.getTotal(), d.getSessions(), d.getLongest(), d.getLastEnd(), d.getName()});

                for (Map.Entry<String, Long> e : d.getDaily().entrySet()) {
                    SqlUtil.upsert(c,
                            "UPDATE `" + dailyTable + "` SET `ms` = `ms` + ? WHERE `uuid` = ? AND `day` = ?",
                            new Object[]{e.getValue(), id, e.getKey()},
                            "INSERT INTO `" + dailyTable + "` (`uuid`, `day`, `ms`) VALUES (?, ?, ?)",
                            new Object[]{id, e.getKey(), e.getValue()});
                }

                for (String claim : d.getClaims()) {
                    boolean exists;
                    try (PreparedStatement ps = SqlUtil.prepare(c,
                            "SELECT 1 FROM `" + claimsTable + "` WHERE `uuid` = ? AND `claim_key` = ?", id, claim);
                         ResultSet rs = ps.executeQuery()) {
                        exists = rs.next();
                    }
                    if (!exists) {
                        try {
                            SqlUtil.update(c, "INSERT INTO `" + claimsTable + "` (`uuid`, `claim_key`, `claimed_at`)"
                                    + " VALUES (?, ?, ?)", id, claim, System.currentTimeMillis());
                        } catch (SQLException dup) {
                            // another server claimed it in the meantime - that is fine
                        }
                    }
                }
                c.commit();
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(oldAuto);
            }
        }
    }

    @Override
    public Map<UUID, PlayerStats> loadAll(String sinceDayKey) throws Exception {
        Map<UUID, long[]> totals = new HashMap<>();
        Map<UUID, String> names = new HashMap<>();
        Map<UUID, Map<String, Long>> daily = new HashMap<>();

        try (Connection c = db.getConnection()) {
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT `uuid`, `playtime_ms`, `sessions`, `longest_ms`, `last_end`, `name` FROM `"
                         + main + "`")) {
                while (rs.next()) {
                    UUID id = parse(rs.getString(1));
                    if (id == null) continue;
                    totals.put(id, new long[]{rs.getLong(2), rs.getInt(3), rs.getLong(4), rs.getLong(5)});
                    names.put(id, rs.getString(6));
                }
            }
            try (PreparedStatement ps = SqlUtil.prepare(c,
                    "SELECT `uuid`, `day`, `ms` FROM `" + dailyTable + "` WHERE `day` >= ?", sinceDayKey);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID id = parse(rs.getString(1));
                    if (id == null) continue;
                    daily.computeIfAbsent(id, k -> new TreeMap<>()).put(rs.getString(2), rs.getLong(3));
                }
            }
        }

        Map<UUID, PlayerStats> out = new HashMap<>();
        for (Map.Entry<UUID, long[]> e : totals.entrySet()) {
            long[] t = e.getValue();
            out.put(e.getKey(), PlayerStats.loaded(e.getKey(), names.get(e.getKey()), t[0], (int) t[1], t[2], t[3],
                    daily.get(e.getKey()), null));
        }
        return out;
    }

    private static UUID parse(String s) {
        try {
            return UUID.fromString(s);
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    public void prune(String beforeDayKey) throws Exception {
        try (Connection c = db.getConnection()) {
            SqlUtil.update(c, "DELETE FROM `" + dailyTable + "` WHERE `day` < ?", beforeDayKey);
        }
    }

    @Override
    public void close() {
        // the shared pool is closed by the DataManager
    }
}
