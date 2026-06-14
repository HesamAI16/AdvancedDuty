package me.hesamai.advancedduty.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.hesamai.advancedduty.Main;
import me.hesamai.advancedduty.duty.DutyManager;
import me.hesamai.advancedduty.duty.playtime.PlaytimeFormatter;
import me.hesamai.advancedduty.duty.playtime.PlaytimeManager;
import me.hesamai.advancedduty.lang.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class DutyExpansion extends PlaceholderExpansion {

    private final Main plugin;
    private final DutyManager dutyManager;
    private final LanguageManager lang;
    private final PlaytimeManager playtimeManager;

    public DutyExpansion(Main plugin) {
        this.plugin = plugin;
        this.dutyManager = plugin.getDutyManager();
        this.lang = plugin.getLanguageManager();
        this.playtimeManager = dutyManager.getPlaytimeManager();
    }

    @Override public @NotNull String getIdentifier() { return "advancedduty"; }
    @Override public @NotNull String getAuthor() { return "hesamai"; }
    @Override public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }
    @Override public boolean canRegister() { return true; }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {

        final String p = params.toLowerCase(Locale.ROOT);

        switch (p) {

            case "staff_online":
                return String.valueOf(
                        Bukkit.getOnlinePlayers().stream()
                                .filter(dutyManager::isOnDuty)
                                .count()
                );

            case "staff_list":
                String list = Bukkit.getOnlinePlayers().stream()
                        .filter(dutyManager::isOnDuty)
                        .map(Player::getName)
                        .collect(Collectors.joining(", "));
                return list.isEmpty() ? lang.getPlaceholder("no_staff_online") : list;

            case "any_staff_online":
                return String.valueOf(
                        Bukkit.getOnlinePlayers().stream()
                                .anyMatch(dutyManager::isOnDuty)
                );
        }

        if (p.startsWith("top_")) {
            return handleTop(p);
        }

        if (player == null) return "";

        final boolean onDuty = dutyManager.isOnDuty(player);
        final UUID uuid = player.getUniqueId();

        switch (p) {

            case "status":
                return onDuty
                        ? lang.getPlaceholder("status.on_duty")
                        : lang.getPlaceholder("status.off_duty");

            case "status_color":
                return onDuty
                        ? lang.getPlaceholder("status_color.on_duty")
                        : lang.getPlaceholder("status_color.off_duty");

            case "is_on_duty":
                return String.valueOf(onDuty);

            case "playtime":
                return PlaytimeFormatter.format(playtimeManager.getTotalPlaytime(uuid));

            case "playtime_ms":
                return String.valueOf(playtimeManager.getTotalPlaytime(uuid));

            case "playtime_seconds":
                return String.valueOf(playtimeManager.getTotalPlaytime(uuid) / 1000);

            case "playtime_minutes":
                return String.valueOf(playtimeManager.getTotalPlaytime(uuid) / 60_000);

            case "playtime_hours":
                return String.valueOf(playtimeManager.getTotalPlaytime(uuid) / 3_600_000);

            case "session_duration":
                return onDuty
                        ? PlaytimeFormatter.format(playtimeManager.getCurrentSessionDuration(uuid))
                        : "0s";

            case "session_duration_ms":
                return String.valueOf(onDuty ? playtimeManager.getCurrentSessionDuration(uuid) : 0L);

            case "rank":
                return String.valueOf(getRank(uuid));

            case "has_session":
                return String.valueOf(playtimeManager.hasActiveSession(uuid));

            case "duty_icon":
                return onDuty
                        ? lang.getPlaceholder("duty_icon.on_duty")
                        : lang.getPlaceholder("duty_icon.off_duty");

            default:
                return null;
        }
    }

    private @Nullable String handleTop(String params) {

        String[] parts = params.split("_");
        if (parts.length < 3) return null;

        int rank;
        try {
            rank = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }

        String field = parts[2];
        if (!field.equals("name") && !field.equals("time")) return null;

        List<Map.Entry<UUID, Long>> sorted = getSorted();

        if (rank < 1 || rank > sorted.size()) {
            return field.equals("name") ? "-" : "0s";
        }

        Map.Entry<UUID, Long> entry = sorted.get(rank - 1);

        if (field.equals("name")) {
            @SuppressWarnings("deprecation")
            String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            return name != null ? name : entry.getKey().toString().substring(0, 8);
        }

        return PlaytimeFormatter.format(entry.getValue());
    }

    private int getRank(UUID uuid) {

        List<Map.Entry<UUID, Long>> sorted = getSorted();

        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getKey().equals(uuid)) {
                return i + 1;
            }
        }

        return 0;
    }

    private List<Map.Entry<UUID, Long>> getSorted() {

        List<Map.Entry<UUID, Long>> sorted =
                new ArrayList<>(playtimeManager.getAllStored().entrySet());

        sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        return sorted;
    }
}