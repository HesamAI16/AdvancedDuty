package me.hesamai.advancedduty.command;

import me.hesamai.advancedduty.Main;
import me.hesamai.advancedduty.duty.playtime.PlaytimeManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Finds players by name without ever contacting Mojang: {@code Bukkit.getOfflinePlayer(String)} can
 * block the server thread for seconds on an unknown name, so only locally known players are used.
 */
public final class PlayerLookup {

    /** A resolved player. */
    public static final class Result {
        public final UUID uuid;
        public final String name;
        public final Player online;

        Result(UUID uuid, String name, Player online) {
            this.uuid = uuid;
            this.name = name;
            this.online = online;
        }
    }

    private PlayerLookup() {
    }

    public static Result find(Main plugin, String name) {
        if (name == null || name.isEmpty()) return null;
        Player exact = Bukkit.getPlayerExact(name);
        if (exact != null) return new Result(exact.getUniqueId(), exact.getName(), exact);

        String lower = name.toLowerCase(Locale.ROOT);
        for (PlaytimeManager.Period period : PlaytimeManager.Period.values()) {
            for (PlaytimeManager.Row row : plugin.getPlaytime().top(period)) {
                String n = row.getName();
                if (n != null && n.equalsIgnoreCase(name)) return new Result(row.getUuid(), n, Bukkit.getPlayer(row.getUuid()));
            }
        }
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.getName() != null && op.getName().toLowerCase(Locale.ROOT).equals(lower)) {
                return new Result(op.getUniqueId(), op.getName(), op.getPlayer());
            }
        }
        Player partial = Bukkit.getPlayer(name);
        return partial == null ? null : new Result(partial.getUniqueId(), partial.getName(), partial);
    }

    public static List<String> onlineNames(String prefix) {
        List<String> out = new ArrayList<>();
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        for (Player pl : Bukkit.getOnlinePlayers()) {
            if (pl.getName().toLowerCase(Locale.ROOT).startsWith(p)) out.add(pl.getName());
        }
        return out;
    }
}
