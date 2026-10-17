package me.hesamai.advancedduty.duty.history;

import me.hesamai.advancedduty.Main;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Thin asynchronous wrapper around {@link HistoryStorage}. */
public class HistoryManager {

    private final Main plugin;

    public HistoryManager(Main plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("history.enabled", true);
    }

    public void record(SessionRecord record) {
        if (!isEnabled()) return;
        final int max = Math.max(10, plugin.getConfig().getInt("history.max-entries-per-player", 200));
        plugin.getData().executor().execute("save duty history", () -> {
            try {
                plugin.getData().history().append(record, max);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    public CompletableFuture<List<SessionRecord>> page(UUID uuid, int limit, int offset) {
        return plugin.getData().executor().submit(() -> plugin.getData().history().load(uuid, limit, offset));
    }

    public CompletableFuture<Integer> count(UUID uuid) {
        return plugin.getData().executor().submit(() -> plugin.getData().history().count(uuid));
    }

    public CompletableFuture<List<SessionRecord>> recent(int limit) {
        return plugin.getData().executor().submit(() -> plugin.getData().history().loadRecent(limit));
    }
}
