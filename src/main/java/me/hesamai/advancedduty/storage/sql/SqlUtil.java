package me.hesamai.advancedduty.storage.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** Dialect independent helpers. */
public final class SqlUtil {

    private SqlUtil() {
    }

    public static PreparedStatement prepare(Connection c, String sql, Object... args) throws SQLException {
        PreparedStatement ps = c.prepareStatement(sql);
        try {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
        } catch (SQLException ex) {
            ps.close();
            throw ex;
        }
        return ps;
    }

    public static int update(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = prepare(c, sql, args)) {
            return ps.executeUpdate();
        }
    }

    /**
     * Portable "insert or update": runs the UPDATE first and only INSERTs when no row matched.
     * If two servers insert the same key at the same moment the loser simply re-runs the UPDATE.
     */
    public static void upsert(Connection c, String updateSql, Object[] updateArgs,
                              String insertSql, Object[] insertArgs) throws SQLException {
        if (update(c, updateSql, updateArgs) > 0) return;
        try {
            update(c, insertSql, insertArgs);
        } catch (SQLException ex) {
            // duplicate key (race with another server) -> the row exists now, update it
            if (update(c, updateSql, updateArgs) == 0) throw ex;
        }
    }
}
