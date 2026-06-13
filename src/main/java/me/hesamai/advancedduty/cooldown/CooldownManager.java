package me.hesamai.advancedduty.cooldown;

import me.hesamai.advancedduty.Main;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CooldownManager {

    private final Main plugin;

    private final Map<UUID, Long> onDutyCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> offDutyCooldown = new ConcurrentHashMap<>();

    public CooldownManager(Main plugin) {
        this.plugin = plugin;
    }

    public boolean canGoOnDuty(Player player) {
        return check(player, onDutyCooldown,
                plugin.getConfig().getInt("duty.cooldown.on-duty"));
    }

    public boolean canGoOffDuty(Player player) {
        return check(player, offDutyCooldown,
                plugin.getConfig().getInt("duty.cooldown.off-duty"));
    }

    private boolean check(Player player, Map<UUID, Long> map, int cooldownSeconds) {

        if (!plugin.getConfig().getBoolean("duty.cooldown.enabled")) {
            return true;
        }

        String bypass = plugin.getConfig()
                .getString("duty.cooldown.bypass-permission");

        if (bypass != null && player.hasPermission(bypass)) {
            return true;
        }

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        Long lastUse = map.get(uuid);
        if (lastUse == null) return true;

        long remaining = (cooldownSeconds * 1000L) - (now - lastUse);

        if (remaining <= 0) return true;

        long seconds = remaining / 1000;

        player.sendMessage(
                plugin.getLanguageManager()
                        .getMessage("cooldown-active", "{time}", String.valueOf(seconds))
        );

        return false;
    }

    public void setOnDuty(Player player) {
        onDutyCooldown.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public void setOffDuty(Player player) {
        offDutyCooldown.put(player.getUniqueId(), System.currentTimeMillis());
    }
}