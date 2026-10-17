package me.hesamai.advancedduty.duty.playtime;

import java.util.Map;
import java.util.UUID;

/** Persistence for {@link PlayerStats}. Every method may block - call only from the storage thread. */
public interface StatsStorage {

    /** @return the stored stats or {@code null} when this player has never been recorded */
    PlayerStats load(UUID uuid) throws Exception;

    /** Applies a batch of changes (additive for totals / daily buckets, max for longest / last-end). */
    void apply(StatsDelta delta) throws Exception;

    /** Every known player with totals and daily buckets from {@code sinceDayKey} on (for leaderboards). */
    Map<UUID, PlayerStats> loadAll(String sinceDayKey) throws Exception;

    /** Deletes daily buckets older than the given day key (exclusive). */
    void prune(String beforeDayKey) throws Exception;

    void close();
}
