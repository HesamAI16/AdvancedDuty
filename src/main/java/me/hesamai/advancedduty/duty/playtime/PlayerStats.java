package me.hesamai.advancedduty.duty.playtime;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * In-memory view of one player's duty statistics plus the not-yet-saved changes ("pending").
 * All methods are synchronized because placeholders may be requested off the main thread.
 */
public final class PlayerStats {

    private final UUID uuid;
    private String name;

    private long total;
    private int sessions;
    private long longest;
    private long lastEnd;
    private final TreeMap<String, Long> daily = new TreeMap<>();
    private final Set<String> claims = new LinkedHashSet<>();

    // ---- pending (not yet persisted)
    private long pTotal;
    private int pSessions;
    private long pLongest;
    private long pLastEnd;
    private boolean pReset;
    private boolean pMeta;
    private final Map<String, Long> pDaily = new HashMap<>();
    private final Set<String> pClaims = new LinkedHashSet<>();

    public PlayerStats(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    /** Creates the in-memory object from persisted values. */
    public static PlayerStats loaded(UUID uuid, String name, long total, int sessions, long longest, long lastEnd,
                                     Map<String, Long> daily, Set<String> claims) {
        PlayerStats s = new PlayerStats(uuid, name);
        s.total = Math.max(0, total);
        s.sessions = Math.max(0, sessions);
        s.longest = Math.max(0, longest);
        s.lastEnd = Math.max(0, lastEnd);
        if (daily != null) s.daily.putAll(daily);
        if (claims != null) s.claims.addAll(claims);
        return s;
    }

    public UUID getUuid() { return uuid; }

    public synchronized String getName() { return name; }

    public synchronized void setName(String name) {
        if (name != null && !name.equals(this.name)) {
            this.name = name;
            pMeta = true;
        }
    }

    public synchronized long getTotal() { return total; }
    public synchronized int getSessions() { return sessions; }
    public synchronized long getLongest() { return longest; }
    public synchronized long getLastEnd() { return lastEnd; }

    public synchronized long getDaily(String dayKey) {
        Long v = daily.get(dayKey);
        return v == null ? 0L : v;
    }

    /** Sum of all day buckets with {@code fromKey <= day <= toKey} (ISO dates sort lexicographically). */
    public synchronized long sumDays(String fromKey, String toKey) {
        long sum = 0;
        for (Long v : daily.subMap(fromKey, true, toKey, true).values()) sum += v;
        return sum;
    }

    public synchronized Map<String, Long> dailySnapshot() {
        return new TreeMap<>(daily);
    }

    public synchronized boolean hasClaim(String key) {
        return claims.contains(key);
    }

    public synchronized Set<String> claimsSnapshot() {
        return new LinkedHashSet<>(claims);
    }

    // ------------------------------------------------------------------ mutation

    public synchronized void accrue(long ms, String dayKey) {
        if (ms <= 0) return;
        total += ms;
        pTotal += ms;
        daily.merge(dayKey, ms, Long::sum);
        pDaily.merge(dayKey, ms, Long::sum);
    }

    public synchronized void sessionEnded(long activeMs, long endMs) {
        sessions++;
        pSessions++;
        if (activeMs > longest) {
            longest = activeMs;
            pLongest = activeMs;
        }
        if (endMs > lastEnd) {
            lastEnd = endMs;
            pLastEnd = endMs;
        }
        pMeta = true;
    }

    /** @return true when the claim was new */
    public synchronized boolean addClaim(String key) {
        if (!claims.add(key)) return false;
        pClaims.add(key);
        return true;
    }

    public synchronized void pruneDaily(String beforeKey) {
        daily.headMap(beforeKey, false).clear();
    }

    /** Zeroes playtime / sessions / history buckets. Claims are kept so rewards cannot be re-earned. */
    public synchronized void reset() {
        total = 0;
        sessions = 0;
        longest = 0;
        lastEnd = 0;
        daily.clear();
        pTotal = 0;
        pSessions = 0;
        pLongest = 0;
        pLastEnd = 0;
        pDaily.clear();
        pReset = true;
        pMeta = true;
    }

    // ------------------------------------------------------------------ persistence

    public synchronized boolean hasPending() {
        return pReset || pMeta || pTotal != 0 || pSessions != 0 || !pDaily.isEmpty() || !pClaims.isEmpty();
    }

    /** Takes the pending changes (clearing them). Returns {@code null} when there is nothing to save. */
    public synchronized StatsDelta drain() {
        if (!hasPending()) return null;
        StatsDelta d = new StatsDelta(uuid, name, pReset, pTotal, pSessions, pLongest, pLastEnd, pDaily, pClaims);
        pTotal = 0;
        pSessions = 0;
        pLongest = 0;
        pLastEnd = 0;
        pReset = false;
        pMeta = false;
        pDaily.clear();
        pClaims.clear();
        return d;
    }

    /** Puts a delta back after a failed write so that nothing is lost. */
    public synchronized void restore(StatsDelta d) {
        if (d == null) return;
        pReset = pReset || d.isReset();
        pTotal += d.getTotal();
        pSessions += d.getSessions();
        pLongest = Math.max(pLongest, d.getLongest());
        pLastEnd = Math.max(pLastEnd, d.getLastEnd());
        for (Map.Entry<String, Long> e : d.getDaily().entrySet()) pDaily.merge(e.getKey(), e.getValue(), Long::sum);
        pClaims.addAll(d.getClaims());
        pMeta = true;
    }
}
