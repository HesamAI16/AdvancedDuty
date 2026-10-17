package me.hesamai.advancedduty.duty.playtime;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * An immutable batch of changes to persist for one player. Storing <b>deltas</b> instead of absolute
 * totals is what makes playtime safe on networks where several servers write the same row.
 */
public final class StatsDelta {

    private final UUID uuid;
    private final String name;
    private final boolean reset;
    private final long total;
    private final int sessions;
    private final long longest;
    private final long lastEnd;
    private final Map<String, Long> daily;
    private final Set<String> claims;

    public StatsDelta(UUID uuid, String name, boolean reset, long total, int sessions, long longest,
                      long lastEnd, Map<String, Long> daily, Set<String> claims) {
        this.uuid = uuid;
        this.name = name;
        this.reset = reset;
        this.total = total;
        this.sessions = sessions;
        this.longest = longest;
        this.lastEnd = lastEnd;
        this.daily = Collections.unmodifiableMap(new TreeMap<>(daily));
        this.claims = Collections.unmodifiableSet(new LinkedHashSet<>(claims));
    }

    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public boolean isReset() { return reset; }
    public long getTotal() { return total; }
    public int getSessions() { return sessions; }
    /** Absolute: the storage keeps the maximum of this and its stored value. */
    public long getLongest() { return longest; }
    /** Absolute: the storage keeps the maximum of this and its stored value. */
    public long getLastEnd() { return lastEnd; }
    public Map<String, Long> getDaily() { return daily; }
    public Set<String> getClaims() { return claims; }
}
