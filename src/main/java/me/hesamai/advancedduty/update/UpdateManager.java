package me.hesamai.advancedduty.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.hesamai.advancedduty.Main;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.function.Consumer;

/** Checks GitHub for newer releases and (optionally) downloads them into the server's update folder. */
public class UpdateManager {

    /** Outcome of a check. */
    public static final class Result {
        public final boolean failed;
        public final boolean updateAvailable;
        public final String latest;
        public final String downloadUrl;
        public final long size;

        Result(boolean failed, boolean updateAvailable, String latest, String downloadUrl, long size) {
            this.failed = failed;
            this.updateAvailable = updateAvailable;
            this.latest = latest;
            this.downloadUrl = downloadUrl;
            this.size = size;
        }
    }

    private static final String API_URL = "https://api.github.com/repos/HesamAI16/AdvancedDuty/releases/latest";

    private final Main plugin;
    private volatile Result last;
    private BukkitTask periodic;

    public UpdateManager(Main plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.getConfig().getBoolean("update.enabled", true)) return;
        if (plugin.getConfig().getBoolean("update.check-on-startup", true)) {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> check(this::onStartupResult), 100L);
        }
        long hours = plugin.getConfig().getLong("update.check-interval-hours", 12);
        if (hours > 0) {
            long ticks = hours * 3600L * 20L;
            periodic = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> check(this::onStartupResult), ticks, ticks);
        }
    }

    public void stop() {
        if (periodic != null) periodic.cancel();
        periodic = null;
    }

    private void onStartupResult(Result r) {
        if (r.failed) return;
        if (!r.updateAvailable) {
            plugin.getLogger().info("Plugin is up to date (v" + plugin.getDescription().getVersion() + ").");
            return;
        }
        plugin.getLogger().info("A new version is available: v" + r.latest + " (you run v" + plugin.getDescription().getVersion() + ")");
        if (r.downloadUrl == null) {
            plugin.getLogger().warning("The release has no downloadable jar attached.");
            return;
        }
        if (plugin.getConfig().getBoolean("update.auto-download", false)) {
            download(r.downloadUrl, r.latest, ok -> { });
        } else {
            plugin.getLogger().info("Run /duty update to download it, or get it from https://github.com/HesamAI16/AdvancedDuty/releases");
        }
    }

    /** Tells staff with {@code advancedduty.update} about a pending update when they join. */
    public void notifyOnJoin(Player p) {
        Result r = last;
        if (r == null || !r.updateAvailable || !plugin.getConfig().getBoolean("update.notify-on-join", true)) return;
        if (!p.hasPermission("advancedduty.update")) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) plugin.getLanguageManager().send(p, "update-found", "{version}", r.latest);
        }, 60L);
    }

    // ------------------------------------------------------------------ check

    /** Runs asynchronously; the callback is invoked from the worker thread. */
    public void check(Consumer<Result> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Result result;
            try {
                result = fetch();
            } catch (Exception ex) {
                plugin.getLogger().warning("Update check failed: " + ex.getMessage());
                result = new Result(true, false, null, null, 0);
            }
            if (!result.failed) last = result;
            callback.accept(result);
        });
    }

    private Result fetch() throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(API_URL).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(8000);
        con.setRequestProperty("Accept", "application/vnd.github+json");
        con.setRequestProperty("User-Agent", "AdvancedDuty/" + plugin.getDescription().getVersion());
        int code = con.getResponseCode();
        if (code != 200) throw new IllegalStateException("GitHub answered HTTP " + code);

        JsonObject json;
        try (InputStream in = con.getInputStream()) {
            json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } finally {
            con.disconnect();
        }
        String tag = json.get("tag_name").getAsString().replaceFirst("^[vV]", "");
        String url = null;
        long size = 0;
        if (json.has("assets") && json.get("assets").isJsonArray()) {
            JsonArray assets = json.getAsJsonArray("assets");
            for (JsonElement el : assets) {
                JsonObject a = el.getAsJsonObject();
                String name = a.get("name").getAsString().toLowerCase(Locale.ROOT);
                if (name.endsWith(".jar") && !name.contains("sources") && !name.contains("javadoc")) {
                    url = a.get("browser_download_url").getAsString();
                    size = a.has("size") ? a.get("size").getAsLong() : 0;
                    break;
                }
            }
        }
        boolean newer = compare(tag, plugin.getDescription().getVersion()) > 0;
        return new Result(false, newer, tag, url, size);
    }

    /** Compares dotted versions numerically ("2.0.10" &gt; "2.0.9"); suffixes such as -SNAPSHOT are ignored. */
    public static int compare(String a, String b) {
        int[] x = parts(a);
        int[] y = parts(b);
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? x[i] : 0;
            int q = i < y.length ? y[i] : 0;
            if (p != q) return Integer.compare(p, q);
        }
        return 0;
    }

    private static int[] parts(String v) {
        String core = v == null ? "" : v.split("[-+ ]")[0];
        String[] seg = core.split("\\.");
        int[] out = new int[seg.length];
        for (int i = 0; i < seg.length; i++) {
            try {
                out[i] = Integer.parseInt(seg[i].replaceAll("\\D", ""));
            } catch (NumberFormatException ex) {
                out[i] = 0;
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ download

    public void download(String downloadUrl, String version, Consumer<Boolean> done) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> done.accept(doDownload(downloadUrl, version)));
    }

    private boolean doDownload(String downloadUrl, String version) {
        File tmp = null;
        try {
            URL url = new URL(downloadUrl);
            String host = url.getHost().toLowerCase(Locale.ROOT);
            if (!url.getProtocol().equals("https") || !(host.equals("github.com") || host.endsWith(".github.com")
                    || host.endsWith(".githubusercontent.com"))) {
                plugin.getLogger().warning("Refusing to download an update from " + host);
                return false;
            }
            File updateFolder = new File(plugin.getDataFolder().getParentFile(), "update");
            if (!updateFolder.exists() && !updateFolder.mkdirs()) {
                plugin.getLogger().warning("Could not create the update folder.");
                return false;
            }
            // the file must carry the name of the installed jar so the server replaces it on restart
            File target = new File(updateFolder, plugin.jarFile().getName());
            tmp = new File(updateFolder, target.getName() + ".part");

            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setConnectTimeout(10000);
            con.setReadTimeout(30000);
            con.setRequestProperty("User-Agent", "AdvancedDuty/" + plugin.getDescription().getVersion());
            if (con.getResponseCode() != 200) {
                plugin.getLogger().warning("Update download failed: HTTP " + con.getResponseCode());
                return false;
            }
            try (InputStream in = con.getInputStream()) {
                Files.copy(in, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } finally {
                con.disconnect();
            }
            if (tmp.length() < 1024 || !looksLikeJar(tmp)) {
                plugin.getLogger().warning("The downloaded file is not a valid jar - discarded.");
                return false;
            }
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("Update v" + version + " downloaded. It is installed on the next server restart.");
            return true;
        } catch (Exception ex) {
            plugin.getLogger().warning("Update download failed: " + ex.getMessage());
            return false;
        } finally {
            if (tmp != null && tmp.exists() && !tmp.delete()) {
                plugin.getLogger().fine("Could not remove " + tmp.getName());
            }
        }
    }

    private static boolean looksLikeJar(File f) throws Exception {
        try (InputStream in = Files.newInputStream(f.toPath())) {
            return in.read() == 'P' && in.read() == 'K';
        }
    }
}
