package me.hesamai.advancedduty.duty.log;

import me.hesamai.advancedduty.Main;
import me.hesamai.advancedduty.discord.DutyWebhookManager;
import me.hesamai.advancedduty.duty.playtime.PlaytimeManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

public class DutyLogManager {

    private final Main plugin;

    public DutyLogManager(Main plugin) {
        this.plugin = plugin;
    }

    public void log(String playerName, boolean onDuty, String reason, long sessionDurationMs) {
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("duty-log.enabled", true)) return;

        String status = onDuty ? "ON-DUTY" : "OFF-DUTY";
        String reasonPart = (reason != null && !reason.isEmpty()) ? " | " + reason : "";

        String timeFmt = cfg.getString("duty-log.time-format", "yyyy-MM-dd HH:mm:ss");
        String time = new SimpleDateFormat(timeFmt).format(new Date());

        String line = cfg.getString("duty-log.format", "[{time}] {player} -> {status}{reason}")
                .replace("{time}", time)
                .replace("{player}", playerName)
                .replace("{status}", status)
                .replace("{reason}", reasonPart);

        if (cfg.getBoolean("duty-log.async", true)) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> write(line));
        } else {
            write(line);
        }

        long totalPlaytime = 0;
        Player player = Bukkit.getPlayerExact(playerName);
        if (player != null) {
            totalPlaytime = plugin.getPlaytimeManager().getTotalPlaytime(player.getUniqueId());
        }
        plugin.getWebhookManager().sendDutyEvent(playerName, onDuty, reason, sessionDurationMs, totalPlaytime);
    }

    public void startCleanupTask() {
        int days = plugin.getConfig().getInt("duty-log.delete-after-days", 0);
        if (days <= 0) return;

        long interval = 20L * 60 * 60 * 24;
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            File folder = getLogFolder();
            if (!folder.exists()) return;
            long cutoff = System.currentTimeMillis() - (long) days * 86_400_000L;
            File[] files = folder.listFiles((d, n) -> n.endsWith(".log"));
            if (files == null) return;
            for (File f : files) {
                if (f.lastModified() < cutoff) f.delete();
            }
        }, interval, interval);
    }

    private void write(String line) {
        File file = resolveLogFile();
        try (PrintWriter writer = new PrintWriter(new FileWriter(file, true))) {
            writer.println(line);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to write duty log: " + ex.getMessage());
        }
    }

    private File resolveLogFile() {
        File folder = getLogFolder();
        folder.mkdirs();

        String mode = plugin.getConfig().getString("duty-log.file-mode", "DAILY");

        if ("SINGLE".equalsIgnoreCase(mode)) {
            return new File(folder, "duty.log");
        }

        String datePart = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        File file = new File(folder, datePart + ".log");
        return file;
    }

    private File getLogFolder() {
        String folder = plugin.getConfig().getString("duty-log.folder", "data/logs");
        return new File(plugin.getDataFolder(), folder);
    }
}