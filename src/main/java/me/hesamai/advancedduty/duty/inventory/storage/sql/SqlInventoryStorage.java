package me.hesamai.advancedduty.duty.inventory.storage.sql;

import me.hesamai.advancedduty.Main;
import me.hesamai.advancedduty.duty.DutyMode;
import me.hesamai.advancedduty.duty.DutySession;
import me.hesamai.advancedduty.duty.DutyStateSnapshot;
import me.hesamai.advancedduty.duty.PlayerState;
import me.hesamai.advancedduty.duty.inventory.DualInventoryProfile;
import me.hesamai.advancedduty.duty.inventory.InventorySerializer;
import me.hesamai.advancedduty.duty.inventory.PlayerInventoryState;
import me.hesamai.advancedduty.duty.inventory.storage.InventoryDataStorage;
import me.hesamai.advancedduty.storage.sql.SqlDatabase;
import me.hesamai.advancedduty.storage.sql.SqlUtil;
import org.bukkit.configuration.file.YamlConfiguration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/** Inventory profiles in a SQL table (MySQL / MariaDB / SQLite). Column layout is unchanged since 1.x. */
public class SqlInventoryStorage implements InventoryDataStorage {

    private final Main plugin;
    private final SqlDatabase db;
    private String table;

    public SqlInventoryStorage(Main plugin, SqlDatabase db) {
        this.plugin = plugin;
        this.db = db;
    }

    @Override
    public void initialize() throws SQLException {
        this.table = db.table("table", "duty_inventories");
        db.execute("CREATE TABLE IF NOT EXISTS `" + table + "` ("
                + " `uuid` VARCHAR(36) NOT NULL PRIMARY KEY,"
                + " `off_contents` LONGTEXT, `off_armor` LONGTEXT, `off_offhand` LONGTEXT, `off_enderchest` LONGTEXT,"
                + " `on_contents` LONGTEXT, `on_armor` LONGTEXT, `on_offhand` LONGTEXT, `on_enderchest` LONGTEXT,"
                + " `off_state` LONGTEXT, `on_state` LONGTEXT,"
                + " `stored_staff_groups` LONGTEXT, `stored_staff_permissions` LONGTEXT,"
                + " `session_data` LONGTEXT,"
                + " `playtime` BIGINT NOT NULL DEFAULT 0,"
                + " `updated_at` BIGINT"
                + ")" + db.tableSuffix());
        // tables created by very old versions
        db.addColumnIfMissing(table, "off_state", "LONGTEXT");
        db.addColumnIfMissing(table, "on_state", "LONGTEXT");
        db.addColumnIfMissing(table, "playtime", "BIGINT NOT NULL DEFAULT 0");
    }

    @Override
    public DutyStateSnapshot load(UUID uuid) throws SQLException {
        DualInventoryProfile profile = new DualInventoryProfile(uuid);
        DutySession session = null;

        try (Connection c = db.getConnection();
             PreparedStatement ps = SqlUtil.prepare(c, "SELECT * FROM `" + table + "` WHERE `uuid` = ?", uuid.toString());
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return null;

            profile.setOffDutyInventory(readInventoryState(
                    rs.getString("off_contents"), rs.getString("off_armor"),
                    rs.getString("off_offhand"), rs.getString("off_enderchest")));
            profile.setOnDutyInventory(readInventoryState(
                    rs.getString("on_contents"), rs.getString("on_armor"),
                    rs.getString("on_offhand"), rs.getString("on_enderchest")));
            profile.setOffDutyState(deserializeState(rs.getString("off_state")));
            profile.setOnDutyState(deserializeState(rs.getString("on_state")));
            profile.setStoredStaffGroups(deserializeStringList(rs.getString("stored_staff_groups")));
            profile.setStoredStaffPermissions(deserializeStringList(rs.getString("stored_staff_permissions")));

            String sessionRaw = rs.getString("session_data");
            if (!isBlank(sessionRaw)) {
                try {
                    YamlConfiguration cfg = new YamlConfiguration();
                    cfg.loadFromString(sessionRaw);
                    session = DutySession.readFrom(cfg);
                } catch (Exception ex) {
                    plugin.getLogger().warning("Failed to parse session_data for " + uuid + ": " + ex.getMessage());
                }
            }
        }
        return new DutyStateSnapshot(profile, session, session != null ? DutyMode.ON_DUTY : DutyMode.OFF_DUTY, 0L);
    }

    @Override
    public void save(UUID uuid, DutyStateSnapshot snapshot) throws SQLException {
        DualInventoryProfile profile = snapshot.getProfile();
        PlayerInventoryState off = profile == null ? null : profile.getOffDutyInventory();
        PlayerInventoryState on = profile == null ? null : profile.getOnDutyInventory();

        Object[] values = new Object[]{
                off == null ? "" : InventorySerializer.itemStackArrayToBase64(off.getContents()),
                off == null ? "" : InventorySerializer.itemStackArrayToBase64(off.getArmorContents()),
                off == null ? "" : InventorySerializer.itemStackToBase64(off.getOffHand()),
                off == null ? "" : InventorySerializer.itemStackArrayToBase64(off.getEnderChestContents()),
                on == null ? "" : InventorySerializer.itemStackArrayToBase64(on.getContents()),
                on == null ? "" : InventorySerializer.itemStackArrayToBase64(on.getArmorContents()),
                on == null ? "" : InventorySerializer.itemStackToBase64(on.getOffHand()),
                on == null ? "" : InventorySerializer.itemStackArrayToBase64(on.getEnderChestContents()),
                serializeState(profile == null ? null : profile.getOffDutyState()),
                serializeState(profile == null ? null : profile.getOnDutyState()),
                serializeStringList(profile == null ? null : profile.getStoredStaffGroups()),
                serializeStringList(profile == null ? null : profile.getStoredStaffPermissions()),
                serializeSession(snapshot.getSession()),
                0L,
                System.currentTimeMillis()
        };

        Object[] updateArgs = new Object[values.length + 1];
        System.arraycopy(values, 0, updateArgs, 0, values.length);
        updateArgs[values.length] = uuid.toString();

        Object[] insertArgs = new Object[values.length + 1];
        insertArgs[0] = uuid.toString();
        System.arraycopy(values, 0, insertArgs, 1, values.length);

        try (Connection c = db.getConnection()) {
            SqlUtil.upsert(c,
                    "UPDATE `" + table + "` SET `off_contents`=?, `off_armor`=?, `off_offhand`=?, `off_enderchest`=?,"
                            + " `on_contents`=?, `on_armor`=?, `on_offhand`=?, `on_enderchest`=?,"
                            + " `off_state`=?, `on_state`=?, `stored_staff_groups`=?, `stored_staff_permissions`=?,"
                            + " `session_data`=?, `playtime`=?, `updated_at`=? WHERE `uuid`=?",
                    updateArgs,
                    "INSERT INTO `" + table + "` (`uuid`, `off_contents`, `off_armor`, `off_offhand`, `off_enderchest`,"
                            + " `on_contents`, `on_armor`, `on_offhand`, `on_enderchest`, `off_state`, `on_state`,"
                            + " `stored_staff_groups`, `stored_staff_permissions`, `session_data`, `playtime`, `updated_at`)"
                            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    insertArgs);
        }
    }

    @Override
    public void delete(UUID uuid) throws SQLException {
        try (Connection c = db.getConnection()) {
            SqlUtil.update(c, "DELETE FROM `" + table + "` WHERE `uuid` = ?", uuid.toString());
        }
    }

    @Override
    public void shutdown() {
        // the shared pool is closed by the DataManager
    }

    private PlayerInventoryState readInventoryState(String contents, String armor, String offHand, String enderChest) {
        if (isBlank(contents) && isBlank(armor) && isBlank(offHand) && isBlank(enderChest)) return null;
        return new PlayerInventoryState(
                InventorySerializer.itemStackArrayFromBase64(contents),
                InventorySerializer.itemStackArrayFromBase64(armor),
                InventorySerializer.itemStackFromBase64(offHand),
                InventorySerializer.itemStackArrayFromBase64(enderChest));
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
