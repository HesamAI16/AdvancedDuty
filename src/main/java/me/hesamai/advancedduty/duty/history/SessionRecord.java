package me.hesamai.advancedduty.duty.history;

import java.util.UUID;

/** One finished duty session. */
public final class SessionRecord {

    private final UUID uuid;
    private final String name;
    private final long startMs;
    private final long endMs;
    private final long activeMs;
    private final String reason;
    private final String endCause;

    public SessionRecord(UUID uuid, String name, long startMs, long endMs, long activeMs, String reason, String endCause) {
        this.uuid = uuid;
        this.name = name;
        this.startMs = startMs;
        this.endMs = endMs;
        this.activeMs = activeMs;
        this.reason = reason;
        this.endCause = endCause;
    }

    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public long getStartMs() { return startMs; }
    public long getEndMs() { return endMs; }
    /** Time actually counted as playtime (AFK-paused time excluded). */
    public long getActiveMs() { return activeMs; }
    public String getReason() { return reason; }
    public String getEndCause() { return endCause; }
}
