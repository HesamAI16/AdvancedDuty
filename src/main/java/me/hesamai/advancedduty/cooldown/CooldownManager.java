package me.hesamai.advancedduty.cooldown;

import me.hesamai.advancedduty.Main;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Anti-spam for /duty. Returns the remaining time instead of messaging, so callers decide how to react. */
public class CooldownManager {

    private final Main plugin;
    private final Map<UUID, Long> onDuty = new ConcurrentHashMap<>();
    private final Map<UUID, Long> offDuty = new ConcurrentHashMap<>();

    public CooldownManager(Main plugin) {
        this.plugin = plugin;
    }

    /** @return remaining seconds (rounded up) or 0 when the player may go on duty */
    public int remainingOn(Player player) {
        return remaining(player, onDuty, plugin.getConfig().getInt("duty.cooldown.on-duty", 30));
    }

    public int remainingOff(Player player) {
        return remaining(player, offDuty, plugin.getConfig().getInt("duty.cooldown.off-duty", 10));
    }

    private int remaining(Player player, Map<UUID, Long> map, int seconds) {
        if (!plugin.getConfig().getBoolean("duty.cooldown.enabled", false) || seconds <= 0) return 0;
        String bypass = plugin.getConfig().getString("duty.cooldown.bypass-permission", "advancedduty.cooldown.bypass");
        if (bypass != null && !bypass.isEmpty() && player.hasPermission(bypass)) return 0;
        Long last = map.get(player.getUniqueId());
        if (last == null) return 0;
        long left = seconds * 1000L - (System.currentTimeMillis() - last);
        return left <= 0 ? 0 : (int) Math.ceil(left / 1000.0);
    }

    public void markOn(Player player) {
        onDuty.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public void markOff(Player player) {
        offDuty.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public void forget(UUID uuid) {
        onDuty.remove(uuid);
        offDuty.remove(uuid);
    }
}
