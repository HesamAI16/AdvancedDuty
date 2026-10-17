package me.hesamai.advancedduty.duty.log;

import me.hesamai.advancedduty.Main;
import me.hesamai.advancedduty.duty.playtime.PlaytimeFormatter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Plain-text duty log (one line per toggle). Writes happen on the storage thread, never on the server thread. */
public class DutyLogManager {

    private final Main plugin;
    private BukkitTask cleanup;

    public DutyLogManager(Main plugin) {
        this.plugin = plugin;
    }

    public void log(String playerName, boolean onDuty, String reason, long sessionDurationMs) {
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("duty-log.enabled", true)) return;

        String reasonText = reason == null ? "" : reason.replace('\n', ' ').replace('\r', ' ').trim();
        String reasonPart = reasonText.isEmpty() ? "" : " | " + reasonText;

        String time;
        try {
            time = new SimpleDateFormat(cfg.getString("duty-log.time-format", "yyyy-MM-dd HH:mm:ss"), Locale.ROOT).format(new Date());
        } catch (IllegalArgumentException ex) {
            time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date());
        }

        final String line = cfg.getString("duty-log.format", "[{time}] {player} -> {status}{reason}")
                .replace("{time}", time)
                .replace("{player}", playerName)
                .replace("{status}", onDuty ? "ON-DUTY" : "OFF-DUTY")
                .replace("{reason}", reasonPart)
                .replace("{duration}", onDuty ? "" : PlaytimeFormatter.format(sessionDurationMs));

        final File file = resolveLogFile();
        if (cfg.getBoolean("duty-log.async", true)) {
            plugin.getData().executor().execute("write duty log", () -> write(file, line));
        } else {
            write(file, line);
        }
    }

    public void startCleanupTask() {
        stop();
        final int days = plugin.getConfig().getInt("duty-log.delete-after-days", 0);
        if (days <= 0) return;
        long interval = 20L * 60 * 60 * 6;
        cleanup = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            File folder = getLogFolder();
            File[] files = folder.listFiles((d, n) -> n.endsWith(".log"));
            if (files == null) return;
            long cutoff = System.currentTimeMillis() - (long) days * 86_400_000L;
            for (File f : files) {
                if (f.lastModified() < cutoff && !f.delete()) {
                    plugin.getLogger().fine("Could not delete old log " + f.getName());
                }
            }
        }, 200L, interval);
    }

    public void stop() {
        if (cleanup != null) cleanup.cancel();
        cleanup = null;
    }

    private void write(File file, String line) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file, true))) {
            writer.println(line);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to write duty log: " + ex.getMessage());
        }
    }

    private File resolveLogFile() {
        File folder = getLogFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create the log folder " + folder);
        }
        String mode = plugin.getConfig().getString("duty-log.file-mode", "DAILY");
        if ("SINGLE".equalsIgnoreCase(mode)) return new File(folder, "duty.log");
        return new File(folder, new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date()) + ".log");
    }

    private File getLogFolder() {
        String folder = plugin.getConfig().getString("duty-log.folder", "data/logs");
        File f = new File(plugin.getDataFolder(), folder);
        // keep the log inside the plugin folder even if someone writes "../../"
        try {
            if (!f.getCanonicalPath().startsWith(plugin.getDataFolder().getCanonicalPath())) {
                return new File(plugin.getDataFolder(), "data/logs");
            }
        } catch (IOException ignored) {
            return new File(plugin.getDataFolder(), "data/logs");
        }
        return f;
    }
}
