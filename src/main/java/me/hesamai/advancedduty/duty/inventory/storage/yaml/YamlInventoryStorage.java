package me.hesamai.advancedduty.duty.inventory.storage.yaml;

import me.hesamai.advancedduty.Main;
import me.hesamai.advancedduty.duty.DutyMode;
import me.hesamai.advancedduty.duty.DutySession;
import me.hesamai.advancedduty.duty.DutyStateSnapshot;
import me.hesamai.advancedduty.duty.PlayerState;
import me.hesamai.advancedduty.duty.inventory.DualInventoryProfile;
import me.hesamai.advancedduty.duty.inventory.InventorySerializer;
import me.hesamai.advancedduty.duty.inventory.PlayerInventoryState;
import me.hesamai.advancedduty.duty.inventory.storage.InventoryDataStorage;
import me.hesamai.advancedduty.storage.yaml.AtomicFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.UUID;

/** {@code data/<uuid>.yml}, written atomically; a ".bak" of the previous version is kept. */
public class YamlInventoryStorage implements InventoryDataStorage {

    private final Main plugin;
    private File folder;

    public YamlInventoryStorage(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public void initialize() {
        String folderName = plugin.getConfig().getString("storage.yaml.folder", "data");
        folder = new File(plugin.getDataFolder(), folderName);
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create " + folder);
        }
    }

    public File getFolder() {
        return folder;
    }

    private File fileFor(UUID uuid) {
        return new File(folder, uuid + ".yml");
    }

    @Override
    public DutyStateSnapshot load(UUID uuid) throws Exception {
        File file = fileFor(uuid);
        if (!file.exists()) return null;

        YamlConfiguration config = AtomicFiles.loadYaml(file); // throws on corrupt YAML -> data is preserved

        DualInventoryProfile profile = new DualInventoryProfile(uuid);
        if (config.contains("inventories.off-duty")) {
            profile.setOffDutyInventory(readState(config, "inventories.off-duty"));
        }
        if (config.contains("inventories.on-duty")) {
            profile.setOnDutyInventory(readState(config, "inventories.on-duty"));
        }
        if (config.contains("state.offduty")) {
            profile.setOffDutyState(PlayerState.read(config.getConfigurationSection("state.offduty")));
        }
        if (config.contains("state.onduty")) {
            profile.setOnDutyState(PlayerState.read(config.getConfigurationSection("state.onduty")));
        }
        profile.setStoredStaffGroups(config.getStringList("staff.stored-groups"));
        profile.setStoredStaffPermissions(config.getStringList("staff.stored-permissions"));

        ConfigurationSection sessionSection = config.getConfigurationSection("session");
        DutySession session = null;
        if (sessionSection != null && sessionSection.getBoolean("active")) {
            session = DutySession.readFrom(sessionSection);
        }
        DutyMode mode = session != null ? DutyMode.ON_DUTY : DutyMode.OFF_DUTY;
        return new DutyStateSnapshot(profile, session, mode, 0L);
    }

    @Override
    public void save(UUID uuid, DutyStateSnapshot snapshot) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        DualInventoryProfile profile = snapshot.getProfile();
        if (profile != null) {
            writeState(config, "inventories.off-duty", profile.getOffDutyInventory());
            writeState(config, "inventories.on-duty", profile.getOnDutyInventory());

            if (profile.getOffDutyState() != null) {
                profile.getOffDutyState().write(config.createSection("state.offduty"));
            }
            if (profile.getOnDutyState() != null) {
                profile.getOnDutyState().write(config.createSection("state.onduty"));
            }
            config.set("staff.stored-groups", profile.getStoredStaffGroups());
            config.set("staff.stored-permissions", profile.getStoredStaffPermissions());
        }

        ConfigurationSection section = config.createSection("session");
        DutySession session = snapshot.getSession();
        if (session != null) {
            section.set("active", true);
            session.writeTo(section);
        } else {
            section.set("active", false);
        }

        AtomicFiles.saveYaml(config, fileFor(uuid), plugin.getConfig().getBoolean("storage.yaml.backup", true));
    }

    private void writeState(YamlConfiguration config, String path, PlayerInventoryState state) {
        if (state == null) return;
        config.set(path + ".contents", InventorySerializer.itemStackArrayToBase64(state.getContents()));
        config.set(path + ".armor", InventorySerializer.itemStackArrayToBase64(state.getArmorContents()));
        config.set(path + ".offhand", InventorySerializer.itemStackToBase64(state.getOffHand()));
        config.set(path + ".enderchest", InventorySerializer.itemStackArrayToBase64(state.getEnderChestContents()));
    }

    private PlayerInventoryState readState(YamlConfiguration config, String path) {
        String contents = config.getString(path + ".contents");
        String armor = config.getString(path + ".armor");
        String offHand = config.getString(path + ".offhand");
        String enderChest = config.getString(path + ".enderchest");
        if (contents == null && armor == null && offHand == null && enderChest == null) {
            return null;
        }
        return new PlayerInventoryState(
                InventorySerializer.itemStackArrayFromBase64(contents),
                InventorySerializer.itemStackArrayFromBase64(armor),
                InventorySerializer.itemStackFromBase64(offHand),
                InventorySerializer.itemStackArrayFromBase64(enderChest)
        );
    }

    @Override
    public void delete(UUID uuid) {
        File f = fileFor(uuid);
        if (f.exists() && !f.delete()) {
            plugin.getLogger().warning("Could not delete " + f.getName());
        }
    }

    @Override
    public void shutdown() {
        // nothing to release
    }
}
