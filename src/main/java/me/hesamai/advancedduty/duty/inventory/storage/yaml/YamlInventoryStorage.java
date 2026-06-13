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
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class YamlInventoryStorage implements InventoryDataStorage {

    private final Main plugin;
    private File folder;

    public YamlInventoryStorage(Main plugin) {
        this.plugin = plugin;}

    @Override
    public void initialize() {

        String folderName = plugin.getConfig().getString("storage.yaml.folder", "data");
        folder = new File(plugin.getDataFolder(), folderName);

        if (!folder.exists()) {
            folder.mkdirs();
        }
    }

    @Override
    public DutyStateSnapshot load(UUID uuid) {

        File file = new File(folder, uuid + ".yml");
        DualInventoryProfile profile = new DualInventoryProfile(uuid);

        if (!file.exists()) {
            return new DutyStateSnapshot(profile, null, DutyMode.OFF_DUTY, 0L);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        if (config.contains("inventories.off-duty"))
            profile.setOffDutyInventory(readState(config, "inventories.off-duty"));

        if (config.contains("inventories.on-duty"))
            profile.setOnDutyInventory(readState(config, "inventories.on-duty"));

        if (config.contains("state.offduty"))
            profile.setOffDutyState(PlayerState.read(config.getConfigurationSection("state.offduty")));

        if (config.contains("state.onduty"))
            profile.setOnDutyState(PlayerState.read(config.getConfigurationSection("state.onduty")));

        profile.setStoredStaffGroups(config.getStringList("staff.stored-groups"));
        profile.setStoredStaffPermissions(config.getStringList("staff.stored-permissions"));

        ConfigurationSection sessionSection = config.getConfigurationSection("session");

        long playtime = (sessionSection != null) ? sessionSection.getLong("playtime", 0L) : 0L;

        DutySession session = null;

        if (sessionSection != null && sessionSection.getBoolean("active")) {
            session = DutySession.readFrom(sessionSection);
        }

        DutyMode mode = (session != null) ? DutyMode.ON_DUTY : DutyMode.OFF_DUTY;
        return new DutyStateSnapshot(profile, session, mode, playtime);
    }

    @Override
    public void save(UUID uuid, DutyStateSnapshot snapshot) {

        File file = new File(folder, uuid + ".yml");
        YamlConfiguration config = new YamlConfiguration();

        DualInventoryProfile profile = snapshot.getProfile();

        if (profile != null) {

            writeState(config, "inventories.off-duty", profile.getOffDutyInventory());
            writeState(config, "inventories.on-duty", profile.getOnDutyInventory());

            if (profile.getOffDutyState() != null) {
                ConfigurationSection s = config.createSection("state.offduty");
                profile.getOffDutyState().write(s);
            }

            if (profile.getOnDutyState() != null) {
                ConfigurationSection s = config.createSection("state.onduty");
                profile.getOnDutyState().write(s);
            }

            config.set("staff.stored-groups", profile.getStoredStaffGroups());
            config.set("staff.stored-permissions", profile.getStoredStaffPermissions());
        }

        DutySession session = snapshot.getSession();

        ConfigurationSection section = config.createSection("session");

        section.set("playtime", snapshot.getTotalPlaytimeMs());

        if (session != null) {
            section.set("active", true);
            session.writeTo(section);
        } else {
            section.set("active", false);
        }

        try {
            config.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save inventory data for " + uuid);
        }
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
        new File(folder, uuid + ".yml").delete();
    }

    @Override
    public void shutdown() {}
}