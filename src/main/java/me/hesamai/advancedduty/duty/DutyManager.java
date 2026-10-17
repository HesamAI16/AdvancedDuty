package me.hesamai.advancedduty.duty;

import me.hesamai.advancedduty.Main;
import me.hesamai.advancedduty.api.DutyCause;
import me.hesamai.advancedduty.api.DutyResult;
import me.hesamai.advancedduty.api.event.DutyStateChangeEvent;
import me.hesamai.advancedduty.api.event.DutyToggleEvent;
import me.hesamai.advancedduty.duty.history.SessionRecord;
import me.hesamai.advancedduty.duty.inventory.DualInventoryProfile;
import me.hesamai.advancedduty.duty.inventory.InventoryUtil;
import me.hesamai.advancedduty.duty.inventory.PlayerInventoryState;
import me.hesamai.advancedduty.duty.profile.ProfileConfig;
import me.hesamai.advancedduty.util.MessageUtil;
import me.hesamai.advancedduty.util.PapiUtil;
import me.hesamai.advancedduty.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * The heart of the plugin: moves staff on and off duty and keeps their state safe.
 * <p>
 * Every public toggle method must be called from the server thread. Data is loaded in
 * {@code AsyncPlayerPreLoginEvent}; a player whose stored data cannot be read is never allowed to
 * toggle (and nothing is ever written for them), so unreadable data is never overwritten.
 */
public class DutyManager {

    private final Main plugin;

    private final Map<UUID, DutySession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, ProfileConfig> active = new ConcurrentHashMap<>();
    private final Map<UUID, DualInventoryProfile> profiles = new ConcurrentHashMap<>();
    private final Map<UUID, DutySession> pending = new ConcurrentHashMap<>();
    private final Set<UUID> loaded = ConcurrentHashMap.newKeySet();
    private final Set<UUID> failed = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> loadedAt = new ConcurrentHashMap<>();
    private BukkitTask autosave;

    public DutyManager(Main plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        long every = Math.max(30, plugin.getConfig().getLong("storage.autosave-seconds", 300)) * 20L;
        autosave = Bukkit.getScheduler().runTaskTimer(plugin, this::autosave, every, every);
    }

    public void stop() {
        if (autosave != null) autosave.cancel();
        autosave = null;
    }

    // ------------------------------------------------------------------ state queries

    public boolean isOnDuty(Player player) {
        return player != null && sessions.containsKey(player.getUniqueId());
    }

    public boolean isOnDuty(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    public Set<UUID> getOnDutyPlayers() {
        return new HashSet<>(sessions.keySet());
    }

    public DutySession getSession(UUID uuid) {
        return sessions.get(uuid);
    }

    public ProfileConfig getActiveProfile(UUID uuid) {
        return active.get(uuid);
    }

    public String toolSetOf(Player p) {
        ProfileConfig pc = active.get(p.getUniqueId());
        return pc != null ? pc.getTools() : "default";
    }

    public boolean isRestoring(UUID uuid) {
        return pending.containsKey(uuid);
    }

    public boolean willRestoreVanished(UUID uuid) {
        DutySession s = pending.get(uuid);
        return s != null && s.isVanished() && plugin.getConfig().getBoolean("storage.restore-session-on-join", true);
    }

    public boolean isProfileLoaded(UUID uuid) {
        return loaded.contains(uuid) && !failed.contains(uuid);
    }

    public boolean isWorldBlocked(World world) {
        String mode = plugin.getConfig().getString("duty.worlds.mode", "OFF").toUpperCase(Locale.ROOT);
        if (mode.equals("OFF") || world == null) return false;
        boolean listed = false;
        for (String w : plugin.getConfig().getStringList("duty.worlds.list")) {
            if (w.equalsIgnoreCase(world.getName())) {
                listed = true;
                break;
            }
        }
        return mode.equals("WHITELIST") ? !listed : listed;
    }

    // ------------------------------------------------------------------ loading

    /** Loads stored data. Safe to call from any thread; blocks until done (used by pre-login). */
    public void preload(UUID uuid) {
        try {
            DutyStateSnapshotHolder holder = plugin.getData().executor().submit(() -> {
                DutyStateSnapshot snap = plugin.getData().inventories().load(uuid);
                try {
                    plugin.getPlaytime().preload(uuid);
                } catch (Exception ex) {
                    plugin.getPlaytime().markLoadFailed(uuid);
                    plugin.getLogger().log(Level.WARNING, "Could not load duty statistics for " + uuid + ": " + ex.getMessage());
                }
                return new DutyStateSnapshotHolder(snap);
            }).get(20, TimeUnit.SECONDS);
            accept(uuid, holder.snapshot);
        } catch (Exception ex) {
            failed.add(uuid);
            loaded.add(uuid);
            plugin.getLogger().log(Level.SEVERE, "Could not load duty data of " + uuid
                    + " - this player cannot use duty until it is readable, and nothing will be overwritten: "
                    + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()));
        }
    }

    private static final class DutyStateSnapshotHolder {
        final DutyStateSnapshot snapshot;

        DutyStateSnapshotHolder(DutyStateSnapshot snapshot) {
            this.snapshot = snapshot;
        }
    }

    private void accept(UUID uuid, DutyStateSnapshot snap) {
        if (snap != null) {
            profiles.put(uuid, snap.getProfile() == null ? new DualInventoryProfile(uuid) : snap.getProfile());
            if (snap.getSession() != null) pending.put(uuid, snap.getSession());
        }
        failed.remove(uuid);
        loaded.add(uuid);
        loadedAt.put(uuid, System.currentTimeMillis());
    }

    private boolean ensureReady(Player p) {
        UUID id = p.getUniqueId();
        if (loaded.contains(id) && !failed.contains(id)) return true;
        // not loaded (reload, or pre-login did not run) or earlier failure: one more try, on the storage thread
        loaded.remove(id);
        failed.remove(id);
        preload(id);
        return loaded.contains(id) && !failed.contains(id);
    }

    // ------------------------------------------------------------------ enable

    public DutyResult enable(Player player, String reason, DutyCause cause, CommandSender by) {
        return enable(player, reason, cause, by, false);
    }

    public DutyResult enable(Player player, String reason, DutyCause cause, CommandSender by, boolean silent) {
        if (!Bukkit.isPrimaryThread()) {
            plugin.getLogger().warning("Duty may only be toggled from the server thread.");
            return DutyResult.ERROR;
        }
        UUID id = player.getUniqueId();
        if (sessions.containsKey(id)) return DutyResult.ALREADY_IN_STATE;
        if (cause != DutyCause.API && !cause.isForced() && !player.hasPermission("advancedduty.use")) {
            return DutyResult.NO_PERMISSION;
        }
        if (!ensureReady(player)) return DutyResult.DATA_NOT_READY;
        if (plugin.getPlaytime().ensure(id, player.getName()) == null) return DutyResult.DATA_NOT_READY;

        if (!cause.isForced() && cause != DutyCause.API) {
            if (isWorldBlocked(player.getWorld())) return DutyResult.WORLD_BLOCKED;
            if (plugin.getCooldownManager().remainingOn(player) > 0) return DutyResult.ON_COOLDOWN;
        }

        DutyToggleEvent event = new DutyToggleEvent(player, true, reason, cause, by);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return DutyResult.CANCELLED;

        try {
            doEnable(player, reason, cause, silent);
            return DutyResult.SUCCESS;
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to put " + player.getName() + " on duty", ex);
            return DutyResult.ERROR;
        }
    }

    private void doEnable(Player player, String reason, DutyCause cause, boolean silent) {
        UUID id = player.getUniqueId();
        FileConfiguration cfg = plugin.getConfig();
        ProfileConfig prof = ProfileConfig.resolve(plugin, player);
        DualInventoryProfile profile = profiles.computeIfAbsent(id, DualInventoryProfile::new);

        profile.setOffDutyState(captureState(player));

        if (separateInventories()) {
            profile.setOffDutyInventory(InventoryUtil.captureWithout(player, includeEnderChest(), plugin.getTools()::isTool));
            PlayerInventoryState onInv = profile.getOnDutyInventory();
            if (onInv == null) {
                if (cfg.getBoolean("duty.inventory.clear-on-first-duty", true)) InventoryUtil.clear(player, includeEnderChest());
            } else {
                InventoryUtil.restore(player, onInv, includeEnderChest());
            }
        }

        DutySession session = createSession(player, reason);
        sessions.put(id, session);
        active.put(id, prof);
        plugin.getPlaytime().startSession(id, player.getName());
        plugin.getAfk().trackPlayer(id);

        // optional: return to the state the player had when the previous duty ended
        if (cfg.getBoolean("duty.remember-duty-state", false) && profile.getOnDutyState() != null) {
            restoreState(player, profile.getOnDutyState());
        }
        applyProfile(player, prof, false);

        plugin.getLuckPerms().enableStaffAccess(player, profile);
        plugin.getLuckPerms().applyMeta(player, true);
        plugin.getLuckPerms().setStaffTag(player, false);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && isOnDuty(player)) plugin.getLuckPerms().applyDutyPrefix(player);
        }, 2L);

        plugin.getTools().give(player, prof.getTools());
        if (prof.isVanish() && plugin.getVanish().isEnabled()) plugin.getVanish().vanish(player, !silent);
        plugin.getDisplays().begin(player, prof);

        plugin.getCooldownManager().markOn(player);
        runCommands(player, prof.getCommandsOn());
        saveAsync(id);

        plugin.getDutyLog().log(player.getName(), true, reason, 0);
        plugin.getDiscord().sendDuty(player, true, reason, 0, plugin.getPlaytime().getTotal(id));
        plugin.getNetwork().publishPresence(player, true);
        if (!silent) announce(player, true, reason);
        selfEffects(player, true);

        Bukkit.getPluginManager().callEvent(new DutyStateChangeEvent(player, true, reason, cause, 0));
    }

    // ------------------------------------------------------------------ disable

    public DutyResult disable(Player player, DutyCause cause, CommandSender by) {
        return disable(player, cause, by, false);
    }

    public DutyResult disable(Player player, DutyCause cause, CommandSender by, boolean silent) {
        if (!Bukkit.isPrimaryThread()) {
            plugin.getLogger().warning("Duty may only be toggled from the server thread.");
            return DutyResult.ERROR;
        }
        UUID id = player.getUniqueId();
        if (!sessions.containsKey(id)) return DutyResult.ALREADY_IN_STATE;

        if (!cause.isForced() && cause != DutyCause.API && plugin.getCooldownManager().remainingOff(player) > 0) {
            return DutyResult.ON_COOLDOWN;
        }
        DutyToggleEvent event = new DutyToggleEvent(player, false, null, cause, by);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return DutyResult.CANCELLED;

        try {
            doDisable(player, cause, silent);
            return DutyResult.SUCCESS;
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to take " + player.getName() + " off duty", ex);
            return DutyResult.ERROR;
        }
    }

    public DutyResult toggle(Player player, String reason, DutyCause cause, CommandSender by) {
        return isOnDuty(player) ? disable(player, cause, by) : enable(player, reason, cause, by);
    }

    private void doDisable(Player player, DutyCause cause, boolean silent) {
        UUID id = player.getUniqueId();
        DutySession session = sessions.remove(id);
        ProfileConfig prof = active.remove(id);
        if (prof == null) prof = ProfileConfig.resolve(plugin, player);
        DualInventoryProfile profile = profiles.computeIfAbsent(id, DualInventoryProfile::new);

        long start = plugin.getPlaytime().sessionStart(id);
        long activeMs = plugin.getPlaytime().endSession(id);
        long end = System.currentTimeMillis();
        recordHistory(player, session, start, end, activeMs, cause);

        profile.setOnDutyState(captureState(player));
        plugin.getTools().strip(player);

        if (separateInventories()) {
            profile.setOnDutyInventory(InventoryUtil.capture(player, includeEnderChest()));
            if (plugin.getConfig().getBoolean("duty.inventory.clear-on-duty-off", false)) {
                profile.setOnDutyInventory(null);
            }
            if (profile.getOffDutyInventory() != null) {
                InventoryUtil.restore(player, profile.getOffDutyInventory(), includeEnderChest());
            } else {
                InventoryUtil.clear(player, includeEnderChest());
            }
        }

        removeProfileEffects(player, prof);
        if (plugin.getVanish().isVanished(id)) plugin.getVanish().unvanish(player, !silent);
        restoreState(player, profile.getOffDutyState());

        plugin.getLuckPerms().removeDutyPrefix(player);
        plugin.getLuckPerms().disableStaffAccess(player, profile);
        plugin.getLuckPerms().applyMeta(player, false);
        plugin.getDisplays().end(player);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !isOnDuty(player)) applyOffDutyVisuals(player);
        }, 2L);

        plugin.getCooldownManager().markOff(player);
        runCommands(player, prof.getCommandsOff());
        saveAsync(id);

        plugin.getDutyLog().log(player.getName(), false, cause == DutyCause.COMMAND || cause == DutyCause.TOOL ? null : cause.name().toLowerCase(Locale.ROOT), activeMs);
        plugin.getDiscord().sendDuty(player, false, session == null ? null : session.getReason(), activeMs, plugin.getPlaytime().getTotal(id));
        plugin.getNetwork().publishPresence(player, false);
        if (!silent) announce(player, false, null);
        selfEffects(player, false);
        plugin.getMilestones().check(player);

        Bukkit.getPluginManager().callEvent(new DutyStateChangeEvent(player, false, null, cause, activeMs));
    }

    private void recordHistory(Player player, DutySession session, long start, long end, long activeMs, DutyCause cause) {
        if (start <= 0) start = session != null ? session.getStartTime() : end - activeMs;
        plugin.getHistory().record(new SessionRecord(player.getUniqueId(), player.getName(), start, end, activeMs,
                session == null ? null : session.getReason(), cause.name()));
    }

    // ------------------------------------------------------------------ join / quit

    public void handleJoin(Player player) {
        UUID id = player.getUniqueId();
        if (!loaded.contains(id)) {
            // pre-login did not run (plugin enabled while online): load asynchronously, then continue
            plugin.getData().executor().submit(() -> {
                preload(id);
                return null;
            }).whenComplete((v, ex) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) continueJoin(player);
            }));
            return;
        }
        continueJoin(player);
    }

    private void continueJoin(Player player) {
        UUID id = player.getUniqueId();
        if (failed.contains(id)) {
            if (player.hasPermission("advancedduty.use")) plugin.getLanguageManager().send(player, "data-not-ready");
            return;
        }
        if (sessions.containsKey(id)) return;

        DutySession stored = pending.remove(id);
        boolean staff = player.hasPermission("advancedduty.use");

        if (stored != null) {
            sessions.put(id, stored);
            if (!staff || !plugin.getConfig().getBoolean("storage.restore-session-on-join", true)) {
                // the session is closed properly instead of leaving the on-duty inventory on a player who is "off duty"
                plugin.getPlaytime().ensure(id, player.getName());
                plugin.getPlaytime().startSession(id, player.getName());
                active.put(id, ProfileConfig.resolve(plugin, player));
                disable(player, DutyCause.JOIN, null, true);
                return;
            }
            resume(player, stored);
            return;
        }

        if (!staff) return;

        FileConfiguration cfg = plugin.getConfig();
        if (cfg.getBoolean("duty.auto-on-join.enabled", false)) {
            String perm = cfg.getString("duty.auto-on-join.permission", "advancedduty.auto");
            if (perm.isEmpty() || player.hasPermission(perm)) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!player.isOnline() || isOnDuty(player)) return;
                    DutyResult r = enable(player, cfg.getString("duty.auto-on-join.reason", ""), DutyCause.AUTO, null, false);
                    if (r == DutyResult.SUCCESS) plugin.getLanguageManager().send(player, "auto-duty-enabled");
                }, Math.max(1, cfg.getLong("duty.auto-on-join.delay-ticks", 20)));
                return;
            }
        }
        applyOffDutyVisuals(player);
        plugin.getNetwork().publishPresence(player, false);
        if (cfg.getBoolean("hooks.luckperms.staff-toggle.enforce-on-join", false)
                && plugin.getLuckPerms().hasTrackedAccess(id)) {
            DualInventoryProfile profile = profiles.computeIfAbsent(id, DualInventoryProfile::new);
            plugin.getLuckPerms().disableStaffAccess(player, profile);
            saveAsync(id);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && plugin.getMilestones().isQuotaEnabled()
                    && plugin.getConfig().getBoolean("quota.notify-on-join", true)) {
                plugin.getMilestones().sendProgress(player, player);
            }
        }, 60L);
    }

    /** A staff member reconnected while on duty: bring the duty features back (no inventory swap). */
    private void resume(Player player, DutySession session) {
        UUID id = player.getUniqueId();
        ProfileConfig prof = ProfileConfig.resolve(plugin, player);
        active.put(id, prof);
        plugin.getPlaytime().startSession(id, player.getName());
        plugin.getAfk().trackPlayer(id);

        if (plugin.getConfig().getBoolean("storage.restore-inventory-on-rejoin", false)) {
            DualInventoryProfile profile = profiles.get(id);
            if (separateInventories() && profile != null && profile.getOnDutyInventory() != null) {
                InventoryUtil.restore(player, profile.getOnDutyInventory(), includeEnderChest());
            }
        }
        applyProfile(player, prof, true);
        plugin.getTools().give(player, prof.getTools());
        if (session.isVanished() && plugin.getVanish().isEnabled()) plugin.getVanish().vanish(player, false);
        plugin.getDisplays().begin(player, prof);
        plugin.getLuckPerms().setStaffTag(player, false);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && isOnDuty(player)) plugin.getLuckPerms().applyDutyPrefix(player);
        }, 2L);
        plugin.getNetwork().publishPresence(player, true);
        plugin.getLanguageManager().send(player, "duty-restored");
    }

    public void handleQuit(Player player) {
        UUID id = player.getUniqueId();
        try {
            if (sessions.containsKey(id)) {
                String mode = plugin.getConfig().getString("duty.on-quit", "KEEP").toUpperCase(Locale.ROOT);
                if (mode.equals("OFF")) doDisable(player, DutyCause.QUIT, true);
                else suspend(player, DutyCause.QUIT);
            }
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.SEVERE, "Error while handling the quit of " + player.getName(), ex);
        } finally {
            cleanup(player);
        }
    }

    /** Ends the playtime of an on-duty player but KEEPS the duty session stored (re-joining restores it). */
    private void suspend(Player player, DutyCause cause) {
        UUID id = player.getUniqueId();
        DutySession session = sessions.get(id);
        DualInventoryProfile profile = profiles.computeIfAbsent(id, DualInventoryProfile::new);

        long start = plugin.getPlaytime().sessionStart(id);
        long activeMs = plugin.getPlaytime().endSession(id);
        recordHistory(player, session, start, System.currentTimeMillis(), activeMs, cause);

        if (session != null) session.setVanished(plugin.getVanish().isVanished(id));
        if (separateInventories()) {
            profile.setOnDutyInventory(InventoryUtil.captureWithout(player, includeEnderChest(), plugin.getTools()::isTool));
        }
        profile.setOnDutyState(captureState(player));
        saveNow(id);

        plugin.getDutyLog().log(player.getName(), false, cause.name().toLowerCase(Locale.ROOT), activeMs);
        plugin.getDiscord().sendDuty(player, false, cause.name().toLowerCase(Locale.ROOT), activeMs, plugin.getPlaytime().getTotal(id));
        plugin.getNetwork().publishPresence(player, false);
        plugin.getMilestones().check(player);
    }

    private void cleanup(Player player) {
        UUID id = player.getUniqueId();
        plugin.getDisplays().end(player);
        plugin.getVanish().release(player);
        plugin.getLuckPerms().forget(id);
        plugin.getNetwork().removePresence(id);
        plugin.getAfk().remove(id);
        sessions.remove(id);
        active.remove(id);
        pending.remove(id);
        if (plugin.getConfig().getBoolean("storage.unload-profile-on-quit", true)) {
            profiles.remove(id);
            loaded.remove(id);
            failed.remove(id);
            loadedAt.remove(id);
        }
        plugin.getPlaytime().unload(id);
    }

    // ------------------------------------------------------------------ shutdown / autosave

    public void shutdown() {
        stop();
        for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            UUID id = player.getUniqueId();
            try {
                if (sessions.containsKey(id)) {
                    String mode = plugin.getConfig().getString("duty.on-quit", "KEEP").toUpperCase(Locale.ROOT);
                    if (mode.equals("OFF")) doDisable(player, DutyCause.SHUTDOWN, true);
                    else suspend(player, DutyCause.SHUTDOWN);
                }
            } catch (Throwable ex) {
                plugin.getLogger().log(Level.SEVERE, "Could not save the duty state of " + player.getName(), ex);
            }
            plugin.getDisplays().end(player);
            plugin.getVanish().release(player);
        }
        plugin.getLuckPerms().clearAllTransient();
        sessions.clear();
        active.clear();
        profiles.clear();
        pending.clear();
        loaded.clear();
        failed.clear();
    }

    private void autosave() {
        long now = System.currentTimeMillis();
        for (UUID id : sessions.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || failed.contains(id)) continue;
            DualInventoryProfile profile = profiles.get(id);
            if (profile != null && separateInventories()) {
                profile.setOnDutyInventory(InventoryUtil.captureWithout(p, includeEnderChest(), plugin.getTools()::isTool));
            }
            DutySession s = sessions.get(id);
            if (s != null) s.setVanished(plugin.getVanish().isVanished(id));
            saveAsync(id);
        }
        // forget pre-loaded data of players who never joined
        for (UUID id : new ArrayList<>(loaded)) {
            Long at = loadedAt.get(id);
            if (at != null && now - at > 120_000L && Bukkit.getPlayer(id) == null && !sessions.containsKey(id)) {
                loaded.remove(id);
                failed.remove(id);
                loadedAt.remove(id);
                profiles.remove(id);
                pending.remove(id);
            }
        }
    }

    // ------------------------------------------------------------------ persistence

    public void saveAsync(UUID uuid) {
        saveNow(uuid);
    }

    private void saveNow(UUID uuid) {
        if (failed.contains(uuid)) return; // never overwrite data we could not read
        DualInventoryProfile profile = profiles.get(uuid);
        if (profile == null) return;
        DutySession session = sessions.get(uuid);
        final DutyStateSnapshot snapshot = new DutyStateSnapshot(profile.copy(), session,
                session != null ? DutyMode.ON_DUTY : DutyMode.OFF_DUTY);
        plugin.getData().executor().execute("save duty state of " + uuid, () -> {
            try {
                plugin.getData().inventories().save(uuid, snapshot);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    // ------------------------------------------------------------------ helpers: state

    private boolean separateInventories() {
        return plugin.getConfig().getBoolean("duty.inventory.separate", false);
    }

    private boolean includeEnderChest() {
        return plugin.getConfig().getBoolean("duty.inventory.include-ender-chest", true);
    }

    private DutySession createSession(Player player, String reason) {
        return new DutySession(player.getUniqueId(), System.currentTimeMillis(), reason, player.getGameMode(),
                player.getAllowFlight(), player.isFlying(), player.getExp(), player.getLevel(), player.getFoodLevel(),
                player.getHealth(), player.getLocation());
    }

    private PlayerState captureState(Player player) {
        return new PlayerState(player.getGameMode(), player.getAllowFlight(), player.isFlying(), player.getExp(),
                player.getLevel(), player.getFoodLevel(), player.getHealth(), player.getLocation());
    }

    private double maxHealth(Player p) {
        AttributeInstance attr = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        return attr == null ? 20.0 : attr.getValue();
    }

    private void restoreState(Player player, PlayerState state) {
        if (state == null) return;
        FileConfiguration cfg = plugin.getConfig();

        if (cfg.getBoolean("duty.restore.gamemode", true) && state.getGameMode() != null) player.setGameMode(state.getGameMode());
        if (cfg.getBoolean("duty.restore.flight", true)) {
            player.setAllowFlight(state.isAllowFlight());
            player.setFlying(state.isAllowFlight() && state.isFlying());
        }
        if (cfg.getBoolean("duty.restore.exp", true)) player.setExp(Math.max(0f, Math.min(1f, state.getExp())));
        if (cfg.getBoolean("duty.restore.level", true)) player.setLevel(Math.max(0, state.getLevel()));
        if (cfg.getBoolean("duty.restore.food", true)) player.setFoodLevel(Math.max(0, Math.min(20, state.getFood())));
        if (cfg.getBoolean("duty.restore.health", true)) {
            player.setHealth(Math.max(1.0, Math.min(state.getHealth(), maxHealth(player))));
        }
        if (cfg.getBoolean("duty.restore.location", false) && state.getLocation() != null) {
            player.teleport(state.getLocation());
        }
    }

    /** Applies the on-duty profile. {@code rejoin} keeps health / game mode as they are. */
    private void applyProfile(Player player, ProfileConfig prof, boolean rejoin) {
        if (!rejoin) {
            if (prof.getGameMode() != null) player.setGameMode(prof.getGameMode());
            if (prof.isFlight()) {
                player.setAllowFlight(true);
                player.setFlying(true);
            }
            if (prof.isHeal()) {
                player.setHealth(maxHealth(player));
                player.setFoodLevel(20);
                player.setSaturation(20f);
                player.setFireTicks(0);
            }
            if (prof.isClearPotions()) {
                for (PotionEffect e : new ArrayList<>(player.getActivePotionEffects())) player.removePotionEffect(e.getType());
            }
        }
        for (PotionEffect e : prof.getEffects()) player.addPotionEffect(e, true);
        if (prof.isGodMode()) player.setInvulnerable(true);
        player.setGlowing(prof.isGlow());
    }

    private void removeProfileEffects(Player player, ProfileConfig prof) {
        for (PotionEffect e : prof.getEffects()) player.removePotionEffect(e.getType());
        if (prof.isGodMode()) player.setInvulnerable(false);
        player.setGlowing(false);
    }

    /** Glow + name tag of staff that are NOT on duty (the old "staff-visibility" feature). */
    public void applyOffDutyVisuals(Player player) {
        if (!player.hasPermission("advancedduty.use") || isOnDuty(player)) return;
        FileConfiguration cfg = plugin.getConfig();
        if (cfg.getBoolean("hooks.luckperms.staff-visibility.enabled", true)) {
            player.setGlowing(cfg.getBoolean("hooks.luckperms.staff-visibility.glow", true));
            plugin.getLuckPerms().setStaffTag(player, true);
        }
    }

    // ------------------------------------------------------------------ helpers: messages / commands

    private void announce(Player player, boolean on, String reason) {
        String path = on ? "announce.on-duty" : "announce.off-duty";
        String mode = plugin.getConfig().getString(path + ".mode", "NONE").toUpperCase(Locale.ROOT);
        if (mode.equals("NONE")) return;
        String msg = plugin.getLanguageManager().raw(on ? "announce-on" : "announce-off",
                "{player}", player.getName(), "{reason}", reason == null || reason.isEmpty()
                        ? plugin.getLanguageManager().gui("no-reason") : reason);
        if (msg.isEmpty()) return;
        boolean vanished = plugin.getVanish().isVanished(player.getUniqueId());
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean staff = p.hasPermission("advancedduty.staffchat.receive") || isOnDuty(p) || p.hasPermission("advancedduty.notify");
            if (mode.equals("ALL") ? !(vanished && !p.hasPermission("advancedduty.vanish.see")) : staff) p.sendMessage(msg);
        }
    }

    private void selfEffects(Player player, boolean on) {
        String path = "effects." + (on ? "on-duty" : "off-duty");
        FileConfiguration cfg = plugin.getConfig();
        if (cfg.getBoolean(path + ".sound.enabled", false)) {
            SoundUtil.play(player, cfg.getString(path + ".sound.name", "ENTITY_EXPERIENCE_ORB_PICKUP"),
                    (float) cfg.getDouble(path + ".sound.volume", 1.0), (float) cfg.getDouble(path + ".sound.pitch", 1.0));
        }
        if (cfg.getBoolean(path + ".title.enabled", false)) {
            player.sendTitle(MessageUtil.color(cfg.getString(path + ".title.title", on ? "&aON DUTY" : "&cOFF DUTY")),
                    MessageUtil.color(cfg.getString(path + ".title.subtitle", "")), 5, 30, 10);
        }
        if (cfg.getBoolean(path + ".actionbar.enabled", false)) {
            MessageUtil.sendActionBar(player, cfg.getString(path + ".actionbar.text", on ? "&aON DUTY" : "&cOFF DUTY"));
        }
    }

    private void runCommands(Player player, List<String> commands) {
        for (String c : commands) runCommand(player, c);
    }

    /** {@code console:<cmd>} (default), {@code player:<cmd>}, {@code message:<text>}, {@code broadcast:<text>}. */
    public void runCommand(Player player, String raw) {
        if (raw == null || raw.trim().isEmpty()) return;
        String cmd = raw.replace("{player}", player.getName()).replace("{uuid}", player.getUniqueId().toString());
        cmd = PapiUtil.apply(player, cmd).trim();
        String lower = cmd.toLowerCase(Locale.ROOT);
        try {
            if (lower.startsWith("player:")) {
                player.performCommand(strip(cmd.substring(7)));
            } else if (lower.startsWith("message:")) {
                player.sendMessage(MessageUtil.color(cmd.substring(8).trim()));
            } else if (lower.startsWith("broadcast:")) {
                Bukkit.broadcastMessage(MessageUtil.color(cmd.substring(10).trim()));
            } else {
                if (lower.startsWith("console:")) cmd = cmd.substring(8);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), strip(cmd));
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Command '" + raw + "' failed: " + ex.getMessage());
        }
    }

    private static String strip(String c) {
        String t = c.trim();
        return t.startsWith("/") ? t.substring(1) : t;
    }

    /** Re-applies visuals for every online player (after /duty reload). */
    public void refreshOnline() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isOnDuty(p)) {
                ProfileConfig prof = ProfileConfig.resolve(plugin, p);
                active.put(p.getUniqueId(), prof);
                plugin.getDisplays().end(p);
                plugin.getDisplays().begin(p, prof);
            } else {
                applyOffDutyVisuals(p);
            }
        }
    }
}
