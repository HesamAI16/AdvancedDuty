package me.hesamai.advancedduty.api;

import me.hesamai.advancedduty.Main;
import me.hesamai.advancedduty.duty.playtime.PlaytimeManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

/**
 * Small, stable entry point for other plugins.
 * <pre>
 *   if (AdvancedDutyAPI.isOnDuty(player)) { ... }
 *   AdvancedDutyAPI.setOnDuty(player, true, "event");
 * </pre>
 * Listen to {@link me.hesamai.advancedduty.api.event.DutyToggleEvent} (cancellable) and
 * {@link me.hesamai.advancedduty.api.event.DutyStateChangeEvent} for changes. Toggle methods must be
 * called from the server thread.
 */
public final class AdvancedDutyAPI {

    private static Main plugin;

    private AdvancedDutyAPI() {
    }

    public static void init(Main main) {
        plugin = main;
    }

    public static void shutdown() {
        plugin = null;
    }

    private static Main plugin() {
        if (plugin == null) throw new IllegalStateException("AdvancedDuty is not enabled");
        return plugin;
    }

    public static boolean isAvailable() {
        return plugin != null;
    }

    public static boolean isOnDuty(Player player) {
        return plugin().getDutyManager().isOnDuty(player);
    }

    public static boolean isOnDuty(UUID uuid) {
        return plugin().getDutyManager().isOnDuty(uuid);
    }

    public static Set<UUID> getOnDutyPlayers() {
        return plugin().getDutyManager().getOnDutyPlayers();
    }

    /** Puts a player on / off duty. Uses {@link DutyCause#API}, so permissions and cooldowns are not checked. */
    public static DutyResult setOnDuty(Player player, boolean onDuty, String reason) {
        return onDuty
                ? plugin().getDutyManager().enable(player, reason, DutyCause.API, null)
                : plugin().getDutyManager().disable(player, DutyCause.API, (CommandSender) null);
    }

    public static boolean isVanished(UUID uuid) {
        return plugin().getVanish().isVanished(uuid);
    }

    public static boolean isFrozen(UUID uuid) {
        return plugin().getFreeze().isFrozen(uuid);
    }

    public static boolean isAfk(UUID uuid) {
        return plugin().getAfk().isAfk(uuid);
    }

    /** Counted on-duty milliseconds (live session included). */
    public static long getPlaytime(UUID uuid, PlaytimeManager.Period period) {
        return plugin().getPlaytime().get(uuid, period);
    }

    public static long getSessionMillis(UUID uuid) {
        return plugin().getPlaytime().sessionMs(uuid);
    }
}
