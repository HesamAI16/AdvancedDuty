package me.hesamai.advancedduty.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.hesamai.advancedduty.Main;
import me.hesamai.advancedduty.duty.playtime.PlaytimeFormatter;
import me.hesamai.advancedduty.feature.StaffEntry;
import me.hesamai.advancedduty.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Discord integration without any extra library:
 * <ul>
 *   <li>webhooks: duty on/off, staff chat, freezes, milestones</li>
 *   <li>a tiny REST "bot" that answers {@code !staff} (and custom text commands) in a channel</li>
 * </ul>
 * All network I/O runs on one background thread; the webhook URL / token never appear in the log.
 */
public class DiscordManager {

    private static final String API = "https://discord.com/api/v10";

    private final Main plugin;
    private ExecutorService http;
    private BukkitTask botTask;
    private volatile String lastMessageId;
    private volatile boolean botInitialised;
    private volatile long blockedUntil;

    public DiscordManager(Main plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ lifecycle

    public void start() {
        stop();
        http = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "AdvancedDuty-Discord");
            t.setDaemon(true);
            return t;
        });
        if (isEnabled() && plugin.getConfig().getBoolean("discord.bot.enabled", false)) {
            String token = plugin.getConfig().getString("discord.bot.token", "");
            String channel = plugin.getConfig().getString("discord.bot.channel-id", "");
            if (token.isEmpty() || channel.isEmpty() || token.startsWith("YOUR_")) {
                plugin.getLogger().warning("[Discord] bot.enabled is true but token / channel-id are not set.");
                return;
            }
            long every = Math.max(2, plugin.getConfig().getLong("discord.bot.poll-seconds", 5)) * 20L;
            botInitialised = false;
            lastMessageId = null;
            botTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> http.execute(this::pollBot), 100L, every);
        }
    }

    public void stop() {
        if (botTask != null) botTask.cancel();
        botTask = null;
        if (http != null) {
            http.shutdown();
            try {
                http.awaitTermination(3, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            http = null;
        }
    }

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("discord.enabled", false);
    }

    // ------------------------------------------------------------------ webhook events

    public void sendDuty(Player player, boolean onDuty, String reason, long sessionMs, long totalMs) {
        if (!isEnabled() || !plugin.getConfig().getBoolean("discord.duty-events.enabled", true)) return;
        if (plugin.getVanish().isVanished(player.getUniqueId())
                && plugin.getConfig().getBoolean("discord.duty-events.hide-vanished", true)) return;
        String url = webhook("");
        if (url == null) return;
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("discord.duty-events");

        JsonObject embed = new JsonObject();
        String title = c.getString(onDuty ? "title-on" : "title-off", onDuty ? "🟢 {player} is now ON DUTY" : "🔴 {player} went OFF DUTY")
                .replace("{player}", player.getName());
        embed.addProperty("title", clip(title, 250));
        embed.addProperty("color", c.getInt(onDuty ? "color-on-duty" : "color-off-duty", onDuty ? 3066993 : 15158332));

        JsonArray fields = new JsonArray();
        if (!onDuty && c.getBoolean("show-duration", true) && sessionMs > 0) {
            fields.add(field("Session Duration", PlaytimeFormatter.format(sessionMs), true));
        }
        if (c.getBoolean("show-playtime-total", true)) fields.add(field("Total Playtime", PlaytimeFormatter.format(totalMs), true));
        if (c.getBoolean("show-reason", true) && onDuty) {
            fields.add(field("Reason", reason == null || reason.isEmpty() ? "None" : reason, true));
        }
        if (c.getBoolean("show-server", false)) fields.add(field("Server", plugin.getConfig().getString("proxy-mode.server-name", "server"), true));
        embed.add("fields", fields);

        String thumb = c.getString("thumbnail", "");
        if (!thumb.isEmpty()) {
            JsonObject t = new JsonObject();
            t.addProperty("url", thumb.replace("{player}", player.getName()).replace("{uuid}", player.getUniqueId().toString()));
            embed.add("thumbnail", t);
        }
        JsonObject footer = new JsonObject();
        footer.addProperty("text", clip(c.getString("footer", "AdvancedDuty"), 200));
        embed.add("footer", footer);
        embed.addProperty("timestamp", java.time.Instant.now().toString());

        sendEmbed(url, embed, null, null);
    }

    /** Generic event (freeze, freeze-quit, milestone). */
    public void sendEvent(String type, String subject, String detail) {
        if (!isEnabled()) return;
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("discord.events." + type);
        if (c == null || !c.getBoolean("enabled", false)) return;
        String url = webhook(c.getString("webhook-url", ""));
        if (url == null) return;

        JsonObject embed = new JsonObject();
        embed.addProperty("title", clip(c.getString("title", type + ": {subject}")
                .replace("{subject}", subject).replace("{detail}", detail == null ? "" : detail), 250));
        String description = c.getString("description", "{detail}").replace("{subject}", subject)
                .replace("{detail}", detail == null ? "" : detail);
        if (!description.trim().isEmpty()) embed.addProperty("description", clip(description, 1800));
        embed.addProperty("color", c.getInt("color", 15105570));
        JsonObject footer = new JsonObject();
        footer.addProperty("text", clip(plugin.getConfig().getString("discord.duty-events.footer", "AdvancedDuty"), 200));
        embed.add("footer", footer);
        embed.addProperty("timestamp", java.time.Instant.now().toString());
        sendEmbed(url, embed, null, null);
    }

    public void sendStaffChat(String playerName, String message) {
        if (!isEnabled() || !plugin.getConfig().getBoolean("discord.staffchat.enabled", false)) return;
        String url = webhook(plugin.getConfig().getString("discord.staffchat.webhook-url", ""));
        if (url == null) return;
        String content = plugin.getConfig().getString("discord.staffchat.format", "**[STAFF]** {player}: {message}")
                .replace("{player}", playerName).replace("{message}", MessageUtil.stripCodes(message));
        JsonObject body = new JsonObject();
        body.addProperty("content", clip(content, 1900));
        body.addProperty("username", clip(plugin.getConfig().getString("discord.staffchat.username", "Staff Chat"), 80));
        String avatar = plugin.getConfig().getString("discord.staffchat.avatar-url", "");
        if (!avatar.isEmpty()) body.addProperty("avatar_url", avatar);
        body.add("allowed_mentions", noMentions());
        post(url, body.toString(), null);
    }

    // ------------------------------------------------------------------ helpers

    private String webhook(String specific) {
        String url = specific != null && !specific.isEmpty() ? specific : plugin.getConfig().getString("discord.webhook-url", "");
        if (url == null || url.isEmpty() || url.contains("YOUR_ID")) return null;
        if (!plugin.getConfig().getBoolean("discord.allow-custom-urls", false)) {
            try {
                String host = new URL(url).getHost().toLowerCase(Locale.ROOT);
                boolean ok = host.equals("discord.com") || host.endsWith(".discord.com")
                        || host.equals("discordapp.com") || host.endsWith(".discordapp.com");
                if (!ok || !url.startsWith("https://")) {
                    plugin.getLogger().warning("[Discord] Ignoring a webhook that is not an https discord.com URL "
                            + "(set discord.allow-custom-urls: true for proxies).");
                    return null;
                }
            } catch (Exception ex) {
                return null;
            }
        }
        return url;
    }

    private JsonObject field(String name, String value, boolean inline) {
        JsonObject f = new JsonObject();
        f.addProperty("name", clip(name, 250));
        f.addProperty("value", clip(value.isEmpty() ? "-" : value, 1000));
        f.addProperty("inline", inline);
        return f;
    }

    private JsonObject noMentions() {
        JsonObject m = new JsonObject();
        m.add("parse", new JsonArray());
        return m;
    }

    private static String clip(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private void sendEmbed(String url, JsonObject embed, String username, String avatar) {
        JsonObject body = new JsonObject();
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        body.add("embeds", embeds);
        body.add("allowed_mentions", noMentions());
        if (username != null) body.addProperty("username", username);
        if (avatar != null) body.addProperty("avatar_url", avatar);
        post(url, body.toString(), null);
    }

    private void post(String url, String json, String botToken) {
        ExecutorService ex = http;
        if (ex == null) return;
        try {
            ex.execute(() -> request("POST", url, json, botToken));
        } catch (RuntimeException ignored) {
            // shutting down
        }
    }

    /** @return response body (may be empty) or {@code null} on failure */
    private String request(String method, String url, String json, String botToken) {
        long wait = blockedUntil - System.currentTimeMillis();
        if (wait > 0) {
            if (wait > 15_000) return null;
            try {
                Thread.sleep(wait);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "AdvancedDuty (https://github.com/HesamAI16/AdvancedDuty, "
                    + plugin.getDescription().getVersion() + ")");
            if (botToken != null) conn.setRequestProperty("Authorization", "Bot " + botToken);
            if (json != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = conn.getResponseCode();
            if (code == 429) {
                double retry = 2.0;
                try {
                    JsonObject o = JsonParser.parseString(read(conn.getErrorStream())).getAsJsonObject();
                    retry = o.get("retry_after").getAsDouble();
                } catch (Exception ignored) {
                    // use the default
                }
                blockedUntil = System.currentTimeMillis() + (long) (retry * 1000) + 100;
                return null;
            }
            if (code < 200 || code >= 300) {
                plugin.getLogger().warning("[Discord] HTTP " + code + " from Discord (" + method + " " + redact(url) + ")");
                return null;
            }
            return read(conn.getInputStream());
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "[Discord] request failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String redact(String url) {
        int i = url.indexOf("/webhooks/");
        return i < 0 ? url : url.substring(0, i + 10) + "***";
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        try (InputStream is = in) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    // ------------------------------------------------------------------ bot (REST polling)

    private void pollBot() {
        String token = plugin.getConfig().getString("discord.bot.token", "");
        String channel = plugin.getConfig().getString("discord.bot.channel-id", "");
        String url = API + "/channels/" + channel + "/messages?limit=20" + (lastMessageId != null ? "&after=" + lastMessageId : "");
        String body = request("GET", url, null, token);
        if (body == null || body.isEmpty()) return;

        try {
            JsonArray array = JsonParser.parseString(body).getAsJsonArray();
            if (array.size() == 0) {
                botInitialised = true;
                return;
            }
            List<JsonObject> messages = new ArrayList<>();
            for (JsonElement el : array) messages.add(el.getAsJsonObject());
            // Discord returns newest first
            messages.sort((a, b) -> Long.compare(Long.parseLong(a.get("id").getAsString()), Long.parseLong(b.get("id").getAsString())));
            lastMessageId = messages.get(messages.size() - 1).get("id").getAsString();

            if (!botInitialised) { // first poll: just remember the newest message, do not answer old ones
                botInitialised = true;
                return;
            }
            for (JsonObject m : messages) {
                JsonObject author = m.getAsJsonObject("author");
                if (author != null && author.has("bot") && author.get("bot").getAsBoolean()) continue;
                String content = m.has("content") ? m.get("content").getAsString().trim() : "";
                if (!content.isEmpty()) handleBotCommand(content, token, channel);
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "[Discord] Could not read channel messages: " + ex.getMessage()
                    + " (is the Message Content intent enabled for the bot?)");
        }
    }

    private void handleBotCommand(String content, String token, String channel) {
        String prefix = plugin.getConfig().getString("discord.bot.prefix", "!");
        String lower = content.toLowerCase(Locale.ROOT);
        if (!lower.startsWith(prefix.toLowerCase(Locale.ROOT))) return;
        String word = lower.substring(prefix.length()).split("\\s+")[0];

        JsonObject reply = null;
        if (plugin.getConfig().getStringList("discord.bot.staff-command.names").stream().anyMatch(word::equalsIgnoreCase)
                || word.equals("staff")) {
            if (!plugin.getConfig().getBoolean("discord.bot.staff-command.enabled", true)) return;
            reply = buildStaffEmbed();
        } else {
            ConfigurationSection custom = plugin.getConfig().getConfigurationSection("discord.bot.commands-custom");
            if (custom != null) {
                for (String key : custom.getKeys(false)) {
                    if (!key.equalsIgnoreCase(word)) continue;
                    String text = custom.getString(key + ".response", "");
                    if (text.isEmpty()) return;
                    reply = new JsonObject();
                    reply.addProperty("content", clip(fillSync(text), 1900));
                    reply.add("allowed_mentions", noMentions());
                }
            }
        }
        if (reply == null) return;
        request("POST", API + "/channels/" + channel + "/messages", reply.toString(), token);
    }

    private String fillSync(String text) {
        try {
            return Bukkit.getScheduler().callSyncMethod(plugin, () -> text
                    .replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()))
                    .replace("{max}", String.valueOf(Bukkit.getMaxPlayers()))
                    .replace("{staff_online}", String.valueOf(plugin.getDutyManager().getOnDutyPlayers().size())))
                    .get(5, TimeUnit.SECONDS);
        } catch (Exception ex) {
            return text;
        }
    }

    /** Builds the {@code !staff} answer. The data is read on the server thread. */
    private JsonObject buildStaffEmbed() {
        List<StaffEntry> staff;
        try {
            staff = Bukkit.getScheduler().callSyncMethod(plugin, () -> plugin.getStaffList().snapshot(false)).get(5, TimeUnit.SECONDS);
        } catch (Exception ex) {
            return null;
        }
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("discord.bot.staff-command");
        boolean includeOff = c != null && c.getBoolean("include-off-duty", true);
        String lineOn = c == null ? "🟢 **{player}** - {time}" : c.getString("line-on", "🟢 **{player}** - {time}");
        String lineOff = c == null ? "⚪ {player}" : c.getString("line-off", "⚪ {player}");

        StringBuilder sb = new StringBuilder();
        int on = 0;
        for (StaffEntry e : staff) {
            if (e.isOnDuty()) {
                on++;
                sb.append(lineOn.replace("{player}", e.getName())
                        .replace("{time}", PlaytimeFormatter.format(Math.max(0, System.currentTimeMillis() - e.getSessionMs())))
                        .replace("{server}", e.getServer() == null ? "" : e.getServer())
                        .replace("{afk}", e.isAfk() ? " (AFK)" : "")).append('\n');
            }
        }
        if (includeOff) {
            for (StaffEntry e : staff) {
                if (!e.isOnDuty()) {
                    sb.append(lineOff.replace("{player}", e.getName())
                            .replace("{server}", e.getServer() == null ? "" : e.getServer())).append('\n');
                }
            }
        }
        if (sb.length() == 0) sb.append(c == null ? "No staff online." : c.getString("empty", "No staff online."));

        JsonObject embed = new JsonObject();
        embed.addProperty("title", (c == null ? "Staff online" : c.getString("title", "Staff online"))
                .replace("{count}", String.valueOf(on)).replace("{total}", String.valueOf(staff.size())));
        embed.addProperty("description", clip(sb.toString(), 3900));
        embed.addProperty("color", c == null ? 3447003 : c.getInt("color", 3447003));
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        JsonObject body = new JsonObject();
        body.add("embeds", embeds);
        body.add("allowed_mentions", noMentions());
        return body;
    }
}
