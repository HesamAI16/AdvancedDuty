package me.hesamai.advancedduty.util;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Version-independent sound lookup. Accepts the old enum style names
 * ({@code BLOCK_NOTE_BLOCK_PLING}) as well as namespaced keys ({@code block.note_block.pling}).
 * Using the {@link Registry} avoids {@code Sound.valueOf}, which is not stable across server versions.
 */
public final class SoundUtil {

    private static final Map<String, Sound> CACHE = new ConcurrentHashMap<>();
    private static final Sound MISSING = null;

    private SoundUtil() {
    }

    public static Sound find(String name) {
        if (name == null || name.trim().isEmpty()) return MISSING;
        String key = name.trim().toLowerCase(Locale.ROOT);
        Sound cached = CACHE.get(key);
        if (cached != null) return cached;

        Sound found = lookup(key);
        if (found != null) CACHE.put(key, found);
        return found;
    }

    private static Sound lookup(String key) {
        try {
            if (key.indexOf('.') >= 0 || key.indexOf(':') >= 0) {
                String k = key.contains(":") ? key : "minecraft:" + key;
                NamespacedKey nk = NamespacedKey.fromString(k);
                if (nk != null) {
                    Sound s = Registry.SOUNDS.get(nk);
                    if (s != null) return s;
                }
            }
            String normalised = key.replace('.', '_').replace(':', '_');
            if (normalised.startsWith("minecraft_")) normalised = normalised.substring("minecraft_".length());
            for (Sound s : Registry.SOUNDS) {
                NamespacedKey sk = s.getKey();
                if (sk == null) continue;
                if (sk.getKey().replace('.', '_').equalsIgnoreCase(normalised)) return s;
            }
        } catch (Throwable ignored) {
            // Registry not available on this server build - fall through.
        }
        return MISSING;
    }

    public static boolean play(Player player, String name, float volume, float pitch) {
        if (player == null) return false;
        Sound sound = find(name);
        if (sound == null) return false;
        player.playSound(player.getLocation(), sound, volume, pitch);
        return true;
    }
}
