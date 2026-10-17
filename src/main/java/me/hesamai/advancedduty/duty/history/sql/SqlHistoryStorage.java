package me.hesamai.advancedduty.duty.history.sql;

import me.hesamai.advancedduty.duty.history.HistoryStorage;
import me.hesamai.advancedduty.duty.history.SessionRecord;
import me.hesamai.advancedduty.storage.sql.SqlDatabase;
import me.hesamai.advancedduty.storage.sql.SqlUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SqlHistoryStorage implements HistoryStorage {

    private final SqlDatabase db;
    private final String table;

    public SqlHistoryStorage(SqlDatabase db) throws SQLException {
        this.db = db;
        this.table = db.table("history-table", "duty_history");
        db.execute("CREATE TABLE IF NOT EXISTS `" + table + "` ("
                + " `uuid` VARCHAR(36) NOT NULL,"
                + " `name` VARCHAR(32),"
                + " `start_ms` BIGINT NOT NULL,"
                + " `end_ms` BIGINT NOT NULL,"
                + " `active_ms` BIGINT NOT NULL DEFAULT 0,"
                + " `reason` VARCHAR(255),"
                + " `cause` VARCHAR(32),"
                + " PRIMARY KEY (`uuid`, `start_ms`)"
                + ")" + db.tableSuffix());
    }

    @Override
    public void append(SessionRecord r, int maxEntries) throws SQLException {
        String reason = r.getReason() == null ? "" : r.getReason();
        if (reason.length() > 255) reason = reason.substring(0, 255);
        try (Connection c = db.getConnection()) {
            SqlUtil.upsert(c,
                    "UPDATE `" + table + "` SET `name` = ?, `end_ms` = ?, `active_ms` = ?, `reason` = ?, `cause` = ?"
                            + " WHERE `uuid` = ? AND `start_ms` = ?",
                    new Object[]{r.getName(), r.getEndMs(), r.getActiveMs(), reason, r.getEndCause(),
                            r.getUuid().toString(), r.getStartMs()},
                    "INSERT INTO `" + table + "` (`uuid`, `name`, `start_ms`, `end_ms`, `active_ms`, `reason`, `cause`)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                    new Object[]{r.getUuid().toString(), r.getName(), r.getStartMs(), r.getEndMs(),
                            r.getActiveMs(), reason, r.getEndCause()});

            if (maxEntries > 0) {
                // keep only the newest N rows of this player
                long cutoff = -1;
                try (PreparedStatement ps = SqlUtil.prepare(c,
                        "SELECT `start_ms` FROM `" + table + "` WHERE `uuid` = ? ORDER BY `start_ms` DESC LIMIT 1 OFFSET "
                                + Math.max(0, maxEntries - 1), r.getUuid().toString());
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) cutoff = rs.getLong(1);
                }
                if (cutoff > 0) {
                    SqlUtil.update(c, "DELETE FROM `" + table + "` WHERE `uuid` = ? AND `start_ms` < ?",
                            r.getUuid().toString(), cutoff);
                }
            }
        }
    }

    @Override
    public List<SessionRecord> load(UUID uuid, int limit, int offset) throws SQLException {
        List<SessionRecord> out = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = SqlUtil.prepare(c,
                     "SELECT `uuid`, `name`, `start_ms`, `end_ms`, `active_ms`, `reason`, `cause` FROM `" + table
                             + "` WHERE `uuid` = ? ORDER BY `start_ms` DESC LIMIT " + Math.max(1, limit)
                             + " OFFSET " + Math.max(0, offset), uuid.toString());
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(map(rs));
        }
        return out;
    }

    @Override
    public int count(UUID uuid) throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = SqlUtil.prepare(c, "SELECT COUNT(*) FROM `" + table + "` WHERE `uuid` = ?", uuid.toString());
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    @Override
    public List<SessionRecord> loadRecent(int limit) throws SQLException {
        List<SessionRecord> out = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = SqlUtil.prepare(c,
                     "SELECT `uuid`, `name`, `start_ms`, `end_ms`, `active_ms`, `reason`, `cause` FROM `" + table
                             + "` ORDER BY `end_ms` DESC LIMIT " + Math.max(1, limit));
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(map(rs));
        }
        return out;
    }

    private SessionRecord map(ResultSet rs) throws SQLException {
        UUID id;
        try {
            id = UUID.fromString(rs.getString(1));
        } catch (IllegalArgumentException ex) {
            id = new UUID(0, 0);
        }
        return new SessionRecord(id, rs.getString(2), rs.getLong(3), rs.getLong(4), rs.getLong(5),
                rs.getString(6), rs.getString(7));
    }

    @Override
    public void close() {
        // the shared pool is closed by the DataManager
    }
}
