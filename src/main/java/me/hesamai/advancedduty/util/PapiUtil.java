package me.hesamai.advancedduty.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

/**
 * Optional PlaceholderAPI bridge. The PlaceholderAPI classes are only touched from {@link Bridge},
 * which is never loaded unless the plugin is actually installed - so the plugin runs without it.
 */
public final class PapiUtil {

    private PapiUtil() {
    }

    public static boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    public static String apply(OfflinePlayer player, String text) {
        if (text == null || text.isEmpty() || text.indexOf('%') < 0) return text == null ? "" : text;
        if (!isAvailable()) return text;
        try {
            return Bridge.apply(player, text);
        } catch (Throwable ex) {
            return text;
        }
    }

    private static final class Bridge {
        static String apply(OfflinePlayer player, String text) {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        }
    }
}
