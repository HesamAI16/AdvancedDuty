package me.hesamai.advancedduty.feature;

import me.hesamai.advancedduty.Main;
import me.hesamai.advancedduty.storage.sql.SqlDatabase;
import me.hesamai.advancedduty.storage.sql.SqlUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Optional multi-server features on top of the shared SQL database (no proxy plugin needed):
 * a network-wide staff list and a network-wide staff chat. Active with {@code proxy-mode.enabled}
 * and MYSQL storage.
 */
public class NetworkManager {

    private final Main plugin;
    private SqlDatabase db;
    private boolean enabled;
    private String server;
    private String presence;
    private String chat;
    private long lastChatId;
    private volatile List<StaffEntry> remote = Collections.emptyList();
    private BukkitTask heartbeat;
    private BukkitTask poller;

    public NetworkManager(Main plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getServerName() {
        return server;
    }

    public void start() {
        stop();
        enabled = false;
        if (!plugin.getConfig().getBoolean("proxy-mode.enabled", false)) return;
        if (plugin.getData().activeType() != me.hesamai.advancedduty.storage.StorageType.MYSQL || plugin.getData().database() == null) {
            plugin.getLogger().warning("proxy-mode.enabled is true but the storage is not MYSQL/MARIADB - network features are off.");
            return;
        }
        db = plugin.getData().database();
        server = plugin.getConfig().getString("proxy-mode.server-name", "server");
        presence = db.table("presence-table", "duty_presence");
        chat = db.table("chat-table", "duty_chat");
        try {
            createTables();
            lastChatId = maxChatId();
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Could not prepare the network tables: " + ex.getMessage());
            return;
        }
        enabled = true;

        long interval = Math.max(10, plugin.getConfig().getLong("proxy-mode.sync-interval-seconds", 30)) * 20L;
        heartbeat = Bukkit.getScheduler().runTaskTimer(plugin, this::heartbeat, 40L, interval);
        if (plugin.getConfig().getBoolean("proxy-mode.network-staffchat", true)) {
            poller = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::pollChat, 60L, 40L);
        }
    }

    public void stop() {
        if (heartbeat != null) heartbeat.cancel();
        if (poller != null) poller.cancel();
        heartbeat = poller = null;
    }

    private void createTables() throws Exception {
        boolean sqlite = db.dialect() == SqlDatabase.Dialect.SQLITE;
        db.execute("CREATE TABLE IF NOT EXISTS `" + presence + "` ("
                + " `uuid` VARCHAR(36) NOT NULL PRIMARY KEY,"
                + " `name` VARCHAR(32) NOT NULL,"
                + " `server` VARCHAR(64) NOT NULL,"
                + " `on_duty` INT NOT NULL DEFAULT 0,"
                + " `since` BIGINT NOT NULL DEFAULT 0,"
                + " `afk` INT NOT NULL DEFAULT 0,"
                + " `vanished` INT NOT NULL DEFAULT 0,"
                + " `updated` BIGINT NOT NULL DEFAULT 0"
                + ")" + db.tableSuffix());
        db.execute("CREATE TABLE IF NOT EXISTS `" + chat + "` ("
                + (sqlite ? " `id` INTEGER PRIMARY KEY AUTOINCREMENT," : " `id` BIGINT AUTO_INCREMENT PRIMARY KEY,")
                + " `server` VARCHAR(64) NOT NULL,"
                + " `sender` VARCHAR(64) NOT NULL,"
                + " `message` VARCHAR(512) NOT NULL,"
                + " `created` BIGINT NOT NULL"
                + ")" + db.tableSuffix());
    }

    private long maxChatId() throws Exception {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT MAX(`id`) FROM `" + chat + "`");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    // ------------------------------------------------------------------ presence

    public List<StaffEntry> getRemoteStaff() {
        return remote;
    }

    private List<StaffEntry> localEntries() {
        List<StaffEntry> list = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.hasPermission("advancedduty.use")) continue;
            UUID id = p.getUniqueId();
            boolean on = plugin.getDutyManager().isOnDuty(id);
            list.add(new StaffEntry(id, p.getName(), server, on, on ? plugin.getPlaytime().sessionStart(id) : 0L,
                    plugin.getAfk().isAfk(id), plugin.getVanish().isVanished(id)));
        }
        return list;
    }

    /** Called on every duty change / join / quit - cheap, the write runs on the storage thread. */
    public void publishPresence(Player p, boolean onDuty) {
        if (!enabled) return;
        final StaffEntry entry = new StaffEntry(p.getUniqueId(), p.getName(), server, onDuty,
                onDuty ? Math.max(plugin.getPlaytime().sessionStart(p.getUniqueId()), 1L) : 0L,
                plugin.getAfk().isAfk(p.getUniqueId()), plugin.getVanish().isVanished(p.getUniqueId()));
        plugin.getData().executor().execute("publish staff presence", () -> {
            try (Connection c = db.getConnection()) {
                upsert(c, entry);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    public void removePresence(UUID id) {
        if (!enabled) return;
        plugin.getData().executor().execute("remove staff presence", () -> {
            try (Connection c = db.getConnection()) {
                SqlUtil.update(c, "DELETE FROM `" + presence + "` WHERE `uuid` = ? AND `server` = ?", id.toString(), server);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    private void upsert(Connection c, StaffEntry e) throws Exception {
        long now = System.currentTimeMillis();
        SqlUtil.upsert(c,
                "UPDATE `" + presence + "` SET `name` = ?, `server` = ?, `on_duty` = ?, `since` = ?, `afk` = ?,"
                        + " `vanished` = ?, `updated` = ? WHERE `uuid` = ?",
                new Object[]{e.getName(), e.getServer(), e.isOnDuty() ? 1 : 0, e.getSessionMs(), e.isAfk() ? 1 : 0,
                        e.isVanished() ? 1 : 0, now, e.getUuid().toString()},
                "INSERT INTO `" + presence + "` (`uuid`, `name`, `server`, `on_duty`, `since`, `afk`, `vanished`, `updated`)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                new Object[]{e.getUuid().toString(), e.getName(), e.getServer(), e.isOnDuty() ? 1 : 0, e.getSessionMs(),
                        e.isAfk() ? 1 : 0, e.isVanished() ? 1 : 0, now});
    }

    private void heartbeat() {
        final List<StaffEntry> local = localEntries();
        final long staleBefore = System.currentTimeMillis()
                - Math.max(10, plugin.getConfig().getLong("proxy-mode.sync-interval-seconds", 30)) * 3000L;
        plugin.getData().executor().execute("network heartbeat", () -> {
            try (Connection c = db.getConnection()) {
                for (StaffEntry e : local) upsert(c, e);
                SqlUtil.update(c, "DELETE FROM `" + presence + "` WHERE `updated` < ?", staleBefore);
                SqlUtil.update(c, "DELETE FROM `" + chat + "` WHERE `created` < ?", System.currentTimeMillis() - 600_000L);

                List<StaffEntry> others = new ArrayList<>();
                try (PreparedStatement ps = SqlUtil.prepare(c,
                        "SELECT `uuid`, `name`, `server`, `on_duty`, `since`, `afk`, `vanished` FROM `" + presence
                                + "` WHERE `server` <> ? AND `updated` >= ?", server, staleBefore);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        try {
                            others.add(new StaffEntry(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getString(3),
                                    rs.getInt(4) == 1, rs.getLong(5), rs.getInt(6) == 1, rs.getInt(7) == 1));
                        } catch (IllegalArgumentException ignored) {
                            // skip malformed row
                        }
                    }
                }
                remote = Collections.unmodifiableList(others);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    // ------------------------------------------------------------------ staff chat

    public void sendChat(String sender, String message) {
        if (!enabled || !plugin.getConfig().getBoolean("proxy-mode.network-staffchat", true)) return;
        final String text = message.length() > 500 ? message.substring(0, 500) : message;
        plugin.getData().executor().execute("network staff chat", () -> {
            try (Connection c = db.getConnection()) {
                SqlUtil.update(c, "INSERT INTO `" + chat + "` (`server`, `sender`, `message`, `created`) VALUES (?, ?, ?, ?)",
                        server, sender, text, System.currentTimeMillis());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    private void pollChat() {
        try (Connection c = db.getConnection();
             PreparedStatement ps = SqlUtil.prepare(c,
                     "SELECT `id`, `server`, `sender`, `message` FROM `" + chat + "` WHERE `id` > ? ORDER BY `id`", lastChatId);
             ResultSet rs = ps.executeQuery()) {
            final List<String[]> incoming = new ArrayList<>();
            while (rs.next()) {
                lastChatId = Math.max(lastChatId, rs.getLong(1));
                if (!server.equals(rs.getString(2))) {
                    incoming.add(new String[]{rs.getString(2), rs.getString(3), rs.getString(4)});
                }
            }
            if (!incoming.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (String[] m : incoming) plugin.getStaffChat().receiveRemote(m[0], m[1], m[2]);
                });
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.FINE, "Network chat poll failed: " + ex.getMessage());
        }
    }
}
