package me.hesamai.advancedduty.storage.sql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.hesamai.advancedduty.Main;
import me.hesamai.advancedduty.storage.StorageType;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

/**
 * One shared connection pool for every SQL backed store (MySQL / MariaDB or a local SQLite file).
 * All statements used by the stores are written to be valid on both dialects.
 */
public final class SqlDatabase implements AutoCloseable {

    public enum Dialect {MYSQL, SQLITE}

    private final HikariDataSource dataSource;
    private final Dialect dialect;
    private final FileConfiguration config;

    private SqlDatabase(HikariDataSource dataSource, Dialect dialect, FileConfiguration config) {
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.config = config;
    }

    public static SqlDatabase open(Main plugin, StorageType type) throws SQLException {
        FileConfiguration cfg = plugin.getConfig();
        HikariConfig hk = new HikariConfig();
        Dialect dialect;

        if (type == StorageType.SQLITE) {
            dialect = Dialect.SQLITE;
            String fileName = cfg.getString("storage.sqlite.file", "data/advancedduty.db");
            File file = new File(plugin.getDataFolder(), fileName);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();

            requireDriver("org.sqlite.JDBC");
            hk.setDriverClassName("org.sqlite.JDBC");
            hk.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
            // SQLite allows a single writer - one connection avoids SQLITE_BUSY altogether.
            hk.setMaximumPoolSize(1);
            hk.setMinimumIdle(1);
            hk.addDataSourceProperty("journal_mode", "WAL");
            hk.addDataSourceProperty("busy_timeout", "5000");
            hk.addDataSourceProperty("synchronous", "NORMAL");
            hk.setPoolName("AdvancedDuty-SQLite");
        } else {
            dialect = Dialect.MYSQL;
            String host = cfg.getString("storage.mysql.host", "localhost");
            int port = cfg.getInt("storage.mysql.port", 3306);
            String database = cfg.getString("storage.mysql.database", "advancedduty");
            boolean useSsl = cfg.getBoolean("storage.mysql.use-ssl", false);
            String extra = cfg.getString("storage.mysql.extra-url-parameters", "");

            String driver = firstDriver("com.mysql.cj.jdbc.Driver", "com.mysql.jdbc.Driver");
            String scheme = "mysql";
            if (driver == null) {
                driver = firstDriver("org.mariadb.jdbc.Driver");
                scheme = "mariadb";
            }
            if (driver == null) {
                throw new SQLException("No MySQL / MariaDB JDBC driver is available on this server.");
            }
            hk.setDriverClassName(driver);

            StringBuilder url = new StringBuilder("jdbc:").append(scheme).append("://")
                    .append(host).append(':').append(port).append('/').append(database)
                    .append("?useSSL=").append(useSsl)
                    .append("&autoReconnect=true&characterEncoding=utf8");
            if (extra != null && !extra.trim().isEmpty()) {
                url.append('&').append(extra.trim().replaceFirst("^[?&]", ""));
            }
            hk.setJdbcUrl(url.toString());
            hk.setUsername(cfg.getString("storage.mysql.username", "root"));
            hk.setPassword(cfg.getString("storage.mysql.password", ""));
            hk.setMaximumPoolSize(Math.max(1, cfg.getInt("storage.mysql.pool-size", 5)));
            hk.setMinimumIdle(1);
            hk.setConnectionTimeout(Math.max(1000L, cfg.getLong("storage.mysql.connection-timeout-ms", 5000L)));
            hk.setIdleTimeout(300_000L);
            hk.setMaxLifetime(Math.max(30_000L, cfg.getLong("storage.mysql.max-lifetime-ms", 600_000L)));
            hk.setPoolName("AdvancedDuty-MySQL");
            hk.addDataSourceProperty("cachePrepStmts", "true");
            hk.addDataSourceProperty("prepStmtCacheSize", "250");
            hk.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        }

        HikariDataSource ds = new HikariDataSource(hk);
        return new SqlDatabase(ds, dialect, cfg);
    }

    private static String firstDriver(String... classNames) {
        for (String name : classNames) {
            try {
                Class.forName(name);
                return name;
            } catch (ClassNotFoundException | LinkageError ignored) {
                // try next
            }
        }
        return null;
    }

    private static void requireDriver(String className) throws SQLException {
        if (firstDriver(className) == null) {
            throw new SQLException("JDBC driver " + className + " is not available on this server.");
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public Dialect dialect() {
        return dialect;
    }

    /** Suffix appended to every CREATE TABLE. */
    public String tableSuffix() {
        return dialect == Dialect.MYSQL ? " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4" : "";
    }

    public String table(String configKey, String defaultName) {
        String name = config.getString("storage.mysql." + configKey, defaultName);
        // table names are interpolated into SQL - keep them strictly alphanumeric
        if (name == null || !name.matches("[A-Za-z0-9_]{1,64}")) return defaultName;
        return name;
    }

    public void execute(String sql) throws SQLException {
        try (Connection c = getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate(sql);
        }
    }

    public boolean columnExists(Connection c, String table, String column) throws SQLException {
        DatabaseMetaData md = c.getMetaData();
        for (String t : new String[]{table, table.toUpperCase(Locale.ROOT), table.toLowerCase(Locale.ROOT)}) {
            try (ResultSet rs = md.getColumns(null, null, t, column)) {
                if (rs.next()) return true;
            }
        }
        return false;
    }

    public void addColumnIfMissing(String table, String column, String definition) throws SQLException {
        try (Connection c = getConnection()) {
            if (columnExists(c, table, column)) return;
            try (Statement s = c.createStatement()) {
                s.executeUpdate("ALTER TABLE `" + table + "` ADD COLUMN `" + column + "` " + definition);
            }
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
    }
}
