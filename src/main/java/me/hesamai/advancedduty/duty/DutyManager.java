package me.hesamai.advancedduty.duty;

import me.hesamai.advancedduty.Main;
import me.hesamai.advancedduty.cooldown.CooldownManager;
import me.hesamai.advancedduty.duty.inventory.DualInventoryProfile;
import me.hesamai.advancedduty.duty.inventory.InventoryUtil;
import me.hesamai.advancedduty.duty.inventory.PlayerInventoryState;
import me.hesamai.advancedduty.duty.inventory.storage.InventoryDataStorage;
import me.hesamai.advancedduty.duty.inventory.storage.StorageType;
import me.hesamai.advancedduty.duty.inventory.storage.mysql.MySqlInventoryStorage;
import me.hesamai.advancedduty.duty.inventory.storage.yaml.YamlInventoryStorage;
import me.hesamai.advancedduty.duty.playtime.PlaytimeManager;
import me.hesamai.advancedduty.hook.LuckPermsHook;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DutyManager {

    private final Main plugin;
    private final StaffVisibilityManager staffVisibilityManager;
    private final DutyMetaManager dutyMetaManager;

    private final LuckPermsHook luckPermsHook;
    private final InventoryDataStorage storage;
    private final PlaytimeManager playtimeManager;

    private final CooldownManager cooldownManager;

    private final Map<UUID, DutySession> sessions = new ConcurrentHashMap<UUID, DutySession>();
    private final Map<UUID, DualInventoryProfile> profiles = new ConcurrentHashMap<UUID, DualInventoryProfile>();
    private final Map<UUID, Boolean> loadedProfiles = new ConcurrentHashMap<UUID, Boolean>();

    public DutyManager(Main plugin, LuckPermsHook luckPermsHook) {
        this.plugin = plugin;
        this.luckPermsHook = luckPermsHook;

        this.cooldownManager = plugin.getCooldownManager();
        this.staffVisibilityManager = new StaffVisibilityManager(plugin, luckPermsHook.getLuckPerms());
        this.dutyMetaManager = new DutyMetaManager(plugin, luckPermsHook.getLuckPerms());
        this.storage = createStorage(plugin);
        this.playtimeManager = new PlaytimeManager(plugin);
    }

    private InventoryDataStorage createStorage(Main plugin) {
        StorageType type = StorageType.fromString(
                plugin.getConfig().getString("storage.type", "YAML")
        );

        if (type == StorageType.MYSQL) {
            try {
                MySqlInventoryStorage mysql = new MySqlInventoryStorage(plugin);
                mysql.initialize();
                return mysql;
            } catch (Exception ex) {
                plugin.getLogger().severe("[AdvancedDuty] MySQL connection failed! Falling back to YAML.");
                plugin.getLogger().severe("[AdvancedDuty] Reason: " + ex.getMessage());
            }
        }

        YamlInventoryStorage yaml = new YamlInventoryStorage(plugin);
        yaml.initialize();
        return yaml;
    }

    public boolean isOnDuty(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public boolean isProfileLoaded(UUID uuid) {
        return loadedProfiles.getOrDefault(uuid, false);
    }

    public boolean toggleDuty(Player player, String reason) {
        if (isOnDuty(player)) {
            return disableDuty(player);
        }

        return enableDuty(player, reason);
    }

    public boolean enableDuty(Player player, String reason) {

        if (isOnDuty(player)) {
            return false;
        }

        if (!cooldownManager.canGoOnDuty(player)) {
            return false;
        }

        UUID uuid = player.getUniqueId();
        ensureProfileReady(uuid);

        DualInventoryProfile profile = profiles
                .computeIfAbsent(uuid, ignored -> new DualInventoryProfile(uuid));

        profile.setOffDutyState(captureState(player));

        if (isSeparateInventoryEnabled()) {
            profile.setOffDutyInventory(
                    InventoryUtil.capture(player, includeEnderChest())
            );

            PlayerInventoryState onDutyInventory = profile.getOnDutyInventory();

            if (onDutyInventory == null) {
                if (clearOnFirstDuty()) {
                    InventoryUtil.clear(player, includeEnderChest());
                }
            } else {
                InventoryUtil.restore(player, onDutyInventory, includeEnderChest());
            }
        }

        sessions.put(uuid, createSession(player, reason));

        if (player.hasPermission("advancedduty.use")) {
            playtimeManager.startSession(uuid);
        }

        applyDutyState(player);
        dutyMetaManager.applyOnDuty(player);
        staffVisibilityManager.removeStaffMeta(player);

        if (luckPermsHook.isAvailable() && luckPermsHook.isStaffToggleEnabled()) {
            luckPermsHook.enableStaffAccess(player, profile);
        }

        scheduleSave(uuid);

        cooldownManager.setOnDuty(player);

        return true;
    }

    public boolean disableDuty(Player player) {
        if (!cooldownManager.canGoOffDuty(player)) {
            return false;
        }

        UUID uuid = player.getUniqueId();
        if (!sessions.containsKey(uuid)) return false;

        sessions.remove(uuid);

        long sessionDuration = 0;
        if (player.hasPermission("advancedduty.use")) {
            sessionDuration = playtimeManager.getCurrentSessionDuration(uuid);
            playtimeManager.endSession(uuid);
        }

        plugin.getDutyLogManager().log(player.getName(), false, null, sessionDuration);

        ensureProfileReady(uuid);

        DualInventoryProfile profile = profiles.computeIfAbsent(uuid, ignored -> new DualInventoryProfile(uuid));
        profile.setOnDutyState(captureState(player));

        if (isSeparateInventoryEnabled()) {
            profile.setOnDutyInventory(InventoryUtil.capture(player, includeEnderChest()));
            if (clearOnDutyOff()) {
                InventoryUtil.clear(player, includeEnderChest());
            } else if (profile.getOffDutyInventory() != null) {
                InventoryUtil.restore(player, profile.getOffDutyInventory(), includeEnderChest());
            }
        }

        restoreState(player, profile.getOffDutyState());

        if (luckPermsHook.isAvailable() && luckPermsHook.isStaffToggleEnabled()) {
            luckPermsHook.disableStaffAccess(player, profile);
        }

        dutyMetaManager.applyOffDuty(player);
        staffVisibilityManager.applyStaffMeta(player);

        scheduleSave(uuid);

        cooldownManager.setOffDuty(player);
        return true;
    }

    public void handleJoin(Player player) {
        UUID uuid = player.getUniqueId();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    DutyStateSnapshot snapshot = storage.load(uuid);
                    DualInventoryProfile profile = snapshot.getProfile() == null
                            ? new DualInventoryProfile(uuid)
                            : snapshot.getProfile();

                    profiles.put(uuid, profile);
                    loadedProfiles.put(uuid, true);

                    playtimeManager.load(uuid);
                    if (snapshot.getTotalPlaytimeMs() > 0 &&
                            player.hasPermission("advancedduty.use")) {

                        playtimeManager.setStoredPlaytime(uuid, snapshot.getTotalPlaytimeMs());
                    }

                    if (snapshot.getSession() != null && plugin.getConfig().getBoolean("storage.restore-session-on-join", true)) {
                        sessions.put(uuid, snapshot.getSession());
                        if (player.hasPermission("advancedduty.use")) {
                            playtimeManager.startSession(player.getUniqueId());
                        }
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            Player online = Bukkit.getPlayer(uuid);
                            if (online == null) return;

                            PlayerInventoryState onDutyInventory = profile.getOnDutyInventory();
                            if (onDutyInventory != null) {
                                InventoryUtil.restore(online, onDutyInventory, includeEnderChest());
                            }

                            applyDutyState(online);
                            dutyMetaManager.applyOnDuty(online);
                            staffVisibilityManager.removeStaffMeta(online);

                            if (luckPermsHook.isAvailable() && luckPermsHook.isStaffToggleEnabled()) {
                                luckPermsHook.enableStaffAccess(online, profile);
                            }
                        });
                    }
                } catch (Exception ex) {
                    plugin.getLogger().warning("Failed to load duty profile for " + player.getName() + ": " + ex.getMessage());
                    profiles.putIfAbsent(uuid, new DualInventoryProfile(uuid));
                    loadedProfiles.put(uuid, true);
                    playtimeManager.load(uuid);
                }
            }
        });
    }

    public void handleQuit(Player player) {
        UUID uuid = player.getUniqueId();
        if (!isProfileLoaded(uuid)) return;

        DualInventoryProfile profile = profiles.computeIfAbsent(uuid, ignored -> new DualInventoryProfile(uuid));

        if (isOnDuty(player)) {
            if (isSeparateInventoryEnabled())
                profile.setOnDutyInventory(InventoryUtil.capture(player, includeEnderChest()));
            if (player.hasPermission("advancedduty.use")) {
                playtimeManager.endSession(player.getUniqueId());
            }
            plugin.getDutyLogManager().log(player.getName(), false, "quit", 0L);
        } else {
            if (isSeparateInventoryEnabled())
                profile.setOffDutyInventory(InventoryUtil.capture(player, includeEnderChest()));
        }

        saveNowAsync(uuid);

        if (plugin.getConfig().getBoolean("storage.unload-profile-on-quit", false)) {
            profiles.remove(uuid);
            loadedProfiles.remove(uuid);playtimeManager.unload(uuid);
        }
    }

    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            DualInventoryProfile profile = profiles.computeIfAbsent(uuid, ignored -> new DualInventoryProfile(uuid));

            if (isSeparateInventoryEnabled()) {
                if (isOnDuty(player)) {
                    profile.setOnDutyInventory(InventoryUtil.capture(player, includeEnderChest()));
                } else {
                    profile.setOffDutyInventory(InventoryUtil.capture(player, includeEnderChest()));
                }
            }

            if (isOnDuty(player)) {
                playtimeManager.endSession(uuid);
                plugin.getDutyLogManager().log(player.getName(), false, "server-shutdown", 0L);
            }

            playtimeManager.saveSync(uuid);
            saveNow(uuid);
        }

        storage.shutdown();
        sessions.clear();
        profiles.clear();
        loadedProfiles.clear();
    }

    public PlaytimeManager getPlaytimeManager() {
        return playtimeManager;
    }

    private void ensureProfileReady(UUID uuid) {
        if (isProfileLoaded(uuid)) {
            profiles.putIfAbsent(uuid, new DualInventoryProfile(uuid));
            return;
        }

        try {
            DutyStateSnapshot snapshot = storage.load(uuid);
            profiles.put(uuid, snapshot.getProfile() == null
                    ? new DualInventoryProfile(uuid)
                    : snapshot.getProfile());

            playtimeManager.load(uuid);

            Player online = Bukkit.getPlayer(uuid);

            if (snapshot.getTotalPlaytimeMs() > 0 &&
                    online != null &&
                    online.hasPermission("advancedduty.use")) {

                playtimeManager.setStoredPlaytime(uuid, snapshot.getTotalPlaytimeMs());
            }

            if (snapshot.getSession() != null) {
                sessions.putIfAbsent(uuid, snapshot.getSession());
            }

        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to synchronously load fallback profile for " + uuid + ": " + ex.getMessage());
            profiles.putIfAbsent(uuid, new DualInventoryProfile(uuid));
        }

        loadedProfiles.put(uuid, true);
    }

    private void scheduleSave(UUID uuid) {
        saveNowAsync(uuid);
    }

    private void saveNowAsync(UUID uuid) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                saveNow(uuid);
            }
        });
    }

    private void saveNow(UUID uuid) {
        DualInventoryProfile profile = profiles.get(uuid);
        DutySession session = sessions.get(uuid);

        if (profile == null) {
            profile = new DualInventoryProfile(uuid);
        }

        DutyMode mode = (session != null) ? DutyMode.ON_DUTY : DutyMode.OFF_DUTY;
        long playtime = playtimeManager.getTotalPlaytime(uuid);

        storage.save(uuid, new DutyStateSnapshot(profile.copy(), session, mode, playtime));
    }

    private DutySession createSession(Player player, String reason) {
        return new DutySession(
                player.getUniqueId(),
                System.currentTimeMillis(),
                reason,
                player.getGameMode(),
                player.getAllowFlight(),
                player.isFlying(),
                player.getExp(),
                player.getLevel(),
                player.getFoodLevel(),
                player.getHealth(),
                player.getLocation()
        );
    }

    private PlayerState captureState(Player player) {

        return new PlayerState(
                player.getGameMode(),
                player.getAllowFlight(),
                player.isFlying(),
                player.getExp(),
                player.getLevel(),
                player.getFoodLevel(),
                player.getHealth(),
                player.getLocation()
        );
    }

    private void restoreState(Player player, PlayerState state) {

        if (state == null) return;

        FileConfiguration config = plugin.getConfig();

        if (config.getBoolean("duty.restore.gamemode", true)) player.setGameMode(state.getGameMode());
        if (config.getBoolean("duty.restore.flight", true)) {
            player.setAllowFlight(state.isAllowFlight());
            player.setFlying(state.isFlying());
        }
        if (config.getBoolean("duty.restore.exp", true))    player.setExp(state.getExp());
        if (config.getBoolean("duty.restore.level", true))  player.setLevel(state.getLevel());
        if (config.getBoolean("duty.restore.food", true))   player.setFoodLevel(state.getFood());
        if (config.getBoolean("duty.restore.health", true)) player.setHealth(Math.min(state.getHealth(), player.getMaxHealth()));
        if (config.getBoolean("duty.restore.location", false) && state.getLocation() != null) {
            player.teleport(state.getLocation());
        }
    }

    private void applyDutyState(Player player) {
        DualInventoryProfile profile = profiles.get(player.getUniqueId());
        if (profile != null && profile.getOnDutyState() != null) {
            restoreState(player, profile.getOnDutyState());
        }

        FileConfiguration config = plugin.getConfig();

        if (config.getBoolean("duty.apply.gamemode.enabled", false)) {
            try {
                player.setGameMode(GameMode.valueOf(
                        config.getString("duty.apply.gamemode.mode", "CREATIVE").toUpperCase()
                ));
            } catch (IllegalArgumentException ignored) {}
        }

        if (config.getBoolean("duty.apply.flight", true)) {
            player.setAllowFlight(true);
            player.setFlying(true);
        }

        if (config.getBoolean("duty.apply.heal", true)) {
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(20);
            player.setFireTicks(0);
        }

        if (config.getBoolean("duty.apply.clear-active-potions", false)) {
            player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));
        }
    }

    private boolean isSeparateInventoryEnabled() {
        return plugin.getConfig().getBoolean("duty.inventory.separate", false);
    }

    private boolean includeEnderChest() {
        return plugin.getConfig().getBoolean("duty.inventory.include-ender-chest", true);
    }

    private boolean clearOnFirstDuty() {
        return plugin.getConfig().getBoolean("duty.inventory.clear-on-first-duty", true);
    }

    private boolean clearOnDutyOff() {
        return plugin.getConfig().getBoolean("duty.inventory.clear-on-duty-off", false);
    }

    public Set<UUID> getOnDutyPlayers() {
        return sessions.keySet();
    }

    public InventoryDataStorage getStorage() {
        return storage;
    }
}