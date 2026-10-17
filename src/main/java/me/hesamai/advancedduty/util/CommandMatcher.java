package me.hesamai.advancedduty.util;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.SimpleCommandMap;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Matches a typed command line against a configured list of command names. Aliases are resolved
 * through the server command map, so blocking "ban" also blocks "/minecraft:ban" and its aliases.
 * A configured entry may end with {@code *} to match by prefix.
 */
public final class CommandMatcher {

    private CommandMatcher() {
    }

    /** Extracts the label (first word, lower-case, without the slash) of a typed command line. */
    public static String label(String message) {
        if (message == null) return "";
        String m = message.trim();
        if (m.startsWith("/")) m = m.substring(1);
        int space = m.indexOf(' ');
        if (space >= 0) m = m.substring(0, space);
        return m.toLowerCase(Locale.ROOT);
    }

    public static boolean matches(String message, List<String> configured) {
        if (configured == null || configured.isEmpty()) return false;
        String label = label(message);
        if (label.isEmpty()) return false;

        Set<String> names = namesFor(label);
        for (String raw : configured) {
            if (raw == null) continue;
            String entry = raw.trim().toLowerCase(Locale.ROOT);
            if (entry.startsWith("/")) entry = entry.substring(1);
            if (entry.isEmpty()) continue;

            boolean prefix = entry.endsWith("*");
            if (prefix) entry = entry.substring(0, entry.length() - 1);

            for (String name : names) {
                if (prefix ? name.startsWith(entry) : name.equals(entry)) return true;
            }
        }
        return false;
    }

    private static Set<String> namesFor(String label) {
        Set<String> names = new HashSet<>();
        names.add(label);
        int colon = label.indexOf(':');
        if (colon >= 0 && colon < label.length() - 1) names.add(label.substring(colon + 1));

        try {
            Command command = lookup(label);
            if (command != null) {
                names.add(command.getName().toLowerCase(Locale.ROOT));
                for (String alias : command.getAliases()) names.add(alias.toLowerCase(Locale.ROOT));
            }
        } catch (Throwable ignored) {
            // best effort only
        }
        return names;
    }

    private static Command lookup(String label) throws Exception {
        Object map = Bukkit.getServer().getClass().getMethod("getCommandMap").invoke(Bukkit.getServer());
        if (map instanceof SimpleCommandMap) {
            return ((SimpleCommandMap) map).getCommand(label);
        }
        Method m = map.getClass().getMethod("getCommand", String.class);
        Object result = m.invoke(map, label);
        return result instanceof Command ? (Command) result : null;
    }
}
