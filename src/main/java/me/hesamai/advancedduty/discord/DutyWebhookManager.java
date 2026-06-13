package me.hesamai.advancedduty.discord;

import me.hesamai.advancedduty.Main;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class DutyWebhookManager {

    private final Main plugin;

    public DutyWebhookManager(Main plugin) {
        this.plugin = plugin;
    }

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("discord.enabled", false);
    }

    // Called when staff goes on/off duty
    public void sendDutyEvent(String playerName, boolean onDuty, String reason, long sessionDurationMs, long totalPlaytimeMs) {
        if (!isEnabled()) return;
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("discord.duty-events.enabled", true)) return;

        String webhookUrl = cfg.getString("discord.webhook-url", "");
        if (webhookUrl.isEmpty()) return;

        int color = onDuty
                ? cfg.getInt("discord.duty-events.color-on-duty", 3066993)
                : cfg.getInt("discord.duty-events.color-off-duty", 15158332);

        String title = onDuty
                ? "🟢 " + playerName + " is now ON DUTY"
                : "🔴 " + playerName + " went OFF DUTY";

        StringBuilder fields = new StringBuilder();

        if (!onDuty && cfg.getBoolean("discord.duty-events.show-duration", true) && sessionDurationMs > 0) {
            fields.append(jsonField("Session Duration", formatDuration(sessionDurationMs), true)).append(",");
        }

        if (cfg.getBoolean("discord.duty-events.show-playtime-total", true)) {
            fields.append(jsonField("Total Playtime", formatDuration(totalPlaytimeMs), true)).append(",");
        }

        if (cfg.getBoolean("discord.duty-events.show-reason", true)) {
            String reasonText = (reason != null && !reason.isEmpty()) ? reason : "None";
            fields.append(jsonField("Reason", reasonText, true)).append(",");
        }

        // Remove trailing comma
        String fieldsStr = fields.length() > 0
                ? fields.substring(0, fields.length() - 1)
                : "";

        String thumbnail = cfg.getString("discord.duty-events.thumbnail", "");
        String thumbnailJson = thumbnail.isEmpty() ? "" : ",\"thumbnail\":{\"url\":\"" + thumbnail + "\"}";

        String footer = cfg.getString("discord.duty-events.footer", "AdvancedDuty");

        String payload = "{\"embeds\":[{"
                + "\"title\":\"" + escapeJson(title) + "\","
                + "\"color\":" + color + ","
                + "\"fields\":[" + fieldsStr + "]"
                + thumbnailJson
                + ",\"footer\":{\"text\":\"" + escapeJson(footer) + "\"}"
                + ",\"timestamp\":\"" + java.time.Instant.now() + "\""
                + "}]}";

        sendAsync(webhookUrl, payload);
    }

    // Called for staff chat messages
    public void sendStaffChat(String playerName, String message) {
        if (!isEnabled()) return;
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("discord.staffchat.enabled", false)) return;

        String webhookUrl = cfg.getString("discord.staffchat.webhook-url", "");
        if (webhookUrl.isEmpty()) webhookUrl = cfg.getString("discord.webhook-url", "");
        if (webhookUrl.isEmpty()) return;

        String format = cfg.getString("discord.staffchat.format", "**[STAFF]** {player}: {message}");
        String content = format.replace("{player}", playerName).replace("{message}", message);

        String username = cfg.getString("discord.staffchat.username", "Staff Chat");
        String avatar = cfg.getString("discord.staffchat.avatar-url", "");

        String payload = "{\"username\":\"" + escapeJson(username) + "\","
                + (avatar.isEmpty() ? "" : "\"avatar_url\":\"" + avatar + "\",")
                + "\"content\":\"" + escapeJson(content) + "\"}";

        sendAsync(webhookUrl, payload);
    }

    private void sendAsync(final String webhookUrl, final String payload) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    HttpURLConnection conn = (HttpURLConnection) new URL(webhookUrl).openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);

                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(payload.getBytes(StandardCharsets.UTF_8));
                    }

                    int code = conn.getResponseCode();
                    if (code != 200 && code != 204) {
                        plugin.getLogger().warning("[Discord] Webhook returned HTTP " + code);
                    }
                    conn.disconnect();
                } catch (Exception ex) {
                    plugin.getLogger().warning("[Discord] Failed to send webhook: " + ex.getMessage());
                }
            }
        });
    }

    private String jsonField(String name, String value, boolean inline) {
        return "{\"name\":\"" + escapeJson(name) + "\","
                + "\"value\":\"" + escapeJson(value) + "\","
                + "\"inline\":" + inline + "}";
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private String formatDuration(long ms) {
        long seconds = ms / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) return hours + "h " + minutes + "m " + secs + "s";
        if (minutes > 0) return minutes + "m " + secs + "s";
        return secs + "s";
    }
}