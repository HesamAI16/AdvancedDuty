package me.hesamai.advancedduty.duty.playtime;

import me.hesamai.advancedduty.Main;
import me.hesamai.advancedduty.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.scheduler.BukkitTask;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Counts on-duty time. Time is accrued from a repeating tick (and exactly at the end of a session),
 * kept in memory per staff member and written to storage as <b>deltas</b> by the storage thread.
 * Leaderboards come from a cached snapshot, so placeholders never touch the database.
 */
public class PlaytimeManager {

    public enum Period {
        ALL, TODAY, WEEK, MONTH;

        public static Period parse(String raw, Period def) {
            if (raw == null) return def;
            switch (raw.toLowerCase().replace("-", "").replace("_", "")) {
                case "all": case "total": case "alltime": return ALL;
                case "today": case "day": case "daily": return TODAY;
                case "week": case "weekly": return WEEK;
                case "month": case "monthly": return MONTH;
                default: return def;
            }
        }
    }

    /** One leaderboard line. */
    public static final class Row {
        private final UUID uuid;
        private final String name;
        private final long ms;

        Row(UUID uuid, String name, long ms) {
            this.uuid = uuid;
            this.name = name;
            this.ms = ms;
        }

        public UUID getUuid() { return uuid; }
        public long getMs() { return ms; }

        /** Best known name (may query the server's offline-player cache - main thread only). */
        public String getName() {
            if (name != null && !name.isEmpty()) return name;
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            return op.getName() != null ? op.getName() : uuid.toString().substring(0, 8);
        }
    }

    private static final class Live {
        final long startMs;
        long lastTick;
        long activeMs;
        boolean paused;

        Live(long now) {
            this.startMs = now;
            this.lastTick = now;
        }
    }

    private final Main plugin;
    private final Map<UUID, PlayerStats> cache = new ConcurrentHashMap<>();
    private final Map<UUID, Live> live = new ConcurrentHashMap<>();
    private final Set<UUID> failedLoads = ConcurrentHashMap.newKeySet();

    private volatile Map<Period, List<Row>> board = new EnumMap<>(Period.class);
    private volatile Map<Period, Map<UUID, Integer>> boardRanks = new EnumMap<>(Period.class);
    private volatile long boardTime;

    private BukkitTask tickTask;
    private BukkitTask flushTask;
    private BukkitTask boardTask;

    public PlaytimeManager(Main plugin) {
        this.plugin = plugin;
        for (Period p : Period.values()) {
            board.put(p, Collections.<Row>emptyList());
            boardRanks.put(p, Collections.<UUID, Integer>emptyMap());
        }
    }

    // ------------------------------------------------------------------ lifecycle

    public void start() {
        stop();
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(System.currentTimeMillis()), 100L, 100L);

        long flush = Math.max(5, plugin.getConfig().getLong("storage.flush-seconds", 30)) * 20L;
        flushTask = Bukkit.getScheduler().runTaskTimer(plugin, this::flushAll, flush, flush);

        long boardEvery = Math.max(15, plugin.getConfig().getLong("playtime.leaderboard-refresh-seconds", 60)) * 20L;
        boardTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshBoard, 40L, boardEvery);
    }

    public void stop() {
        if (tickTask != null) tickTask.cancel();
        if (flushTask != null) flushTask.cancel();
        if (boardTask != null) boardTask.cancel();
        tickTask = flushTask = boardTask = null;
    }

    /** Ends every running session and queues all unsaved data. Call before DataManager.shutdown(). */
    public void shutdown() {
        stop();
        long now = System.currentTimeMillis();
        for (UUID id : new ArrayList<>(live.keySet())) endSession(id, now);
        flushAll();
    }

    // ------------------------------------------------------------------ loading

    /** Loads stats from storage (blocking, storage thread or async pre-login). */
    public void preload(UUID uuid) throws Exception {
        if (cache.containsKey(uuid)) return;
        PlayerStats s = plugin.getData().stats().load(uuid);
        if (s != null) cache.putIfAbsent(uuid, s);
        failedLoads.remove(uuid);
    }

    public void markLoadFailed(UUID uuid) {
        failedLoads.add(uuid);
    }

    /**
     * Returns the stats object for a staff member, creating it when the player has never been
     * recorded. Falls back to a synchronous load if pre-login did not run.
     *
     * @return {@code null} if the stored data is unreadable (callers must not count time then)
     */
    public PlayerStats ensure(UUID uuid, String name) {
        PlayerStats s = cache.get(uuid);
        if (s != null) {
            s.setName(name);
            return s;
        }
        try {
            PlayerStats loaded = plugin.getData().stats().load(uuid);
            s = loaded != null ? loaded : new PlayerStats(uuid, name);
            PlayerStats prev = cache.putIfAbsent(uuid, s);
            if (prev != null) s = prev;
            failedLoads.remove(uuid);
            s.setName(name);
            return s;
        } catch (Exception ex) {
            failedLoads.add(uuid);
            plugin.getLogger().log(Level.WARNING, "Could not load duty statistics of " + name + ": " + ex.getMessage());
            return null;
        }
    }

    public PlayerStats cached(UUID uuid) {
        return cache.get(uuid);
    }

    /** Stats of any player: from memory, else asynchronously from storage ({@code null} if never recorded). */
    public CompletableFuture<PlayerStats> fetch(UUID uuid) {
        PlayerStats s = cache.get(uuid);
        if (s != null) return CompletableFuture.completedFuture(s);
        return plugin.getData().executor().submit(() -> plugin.getData().stats().load(uuid));
    }

    /** Frees memory of an offline, idle player after saving. */
    public void unload(UUID uuid) {
        if (live.containsKey(uuid)) return;
        PlayerStats s = cache.get(uuid);
        if (s == null) return;
        flush(uuid, s);
        cache.remove(uuid);
    }

    // ------------------------------------------------------------------ sessions

    public boolean hasSession(UUID uuid) {
        return live.containsKey(uuid);
    }

    public Set<UUID> sessionIds() {
        return new HashSet<>(live.keySet());
    }

    public int sessionCount() {
        return live.size();
    }

    public void startSession(UUID uuid, String name) {
        if (live.containsKey(uuid)) return;
        if (ensure(uuid, name) == null) return;
        live.put(uuid, new Live(System.currentTimeMillis()));
    }

    public void pause(UUID uuid) {
        Live l = live.get(uuid);
        if (l == null) return;
        long now = System.currentTimeMillis();
        accrue(uuid, l, now);
        synchronized (l) {
            l.paused = true;
        }
    }

    public void resume(UUID uuid) {
        Live l = live.get(uuid);
        if (l == null) return;
        synchronized (l) {
            l.paused = false;
            l.lastTick = System.currentTimeMillis();
        }
    }

    public boolean isPaused(UUID uuid) {
        Live l = live.get(uuid);
        return l != null && l.paused;
    }

    /** @return counted milliseconds of the session that just ended (0 if there was none) */
    public long endSession(UUID uuid) {
        return endSession(uuid, System.currentTimeMillis());
    }

    private long endSession(UUID uuid, long now) {
        Live l = live.remove(uuid);
        if (l == null) return 0L;
        PlayerStats s = cache.get(uuid);
        long active;
        synchronized (l) {
            if (!l.paused && s != null) {
                long delta = Math.max(0, now - l.lastTick);
                s.accrue(delta, dayKey(now));
                l.activeMs += delta;
            }
            active = l.activeMs;
        }
        if (s != null) {
            s.sessionEnded(active, now);
            flush(uuid, s);
        }
        return active;
    }

    /** Length of the running session including the not yet ticked part. */
    public long sessionMs(UUID uuid) {
        Live l = live.get(uuid);
        if (l == null) return 0L;
        synchronized (l) {
            long extra = l.paused ? 0 : Math.max(0, System.currentTimeMillis() - l.lastTick);
            return l.activeMs + extra;
        }
    }

    public long sessionStart(UUID uuid) {
        Live l = live.get(uuid);
        return l == null ? 0L : l.startMs;
    }

    private void tick(long now) {
        for (Map.Entry<UUID, Live> e : live.entrySet()) {
            accrue(e.getKey(), e.getValue(), now);
        }
    }

    private void accrue(UUID uuid, Live l, long now) {
        PlayerStats s = cache.get(uuid);
        synchronized (l) {
            long delta = Math.max(0, now - l.lastTick);
            l.lastTick = now;
            if (l.paused || s == null || delta == 0) return;
            s.accrue(delta, dayKey(now));
            l.activeMs += delta;
        }
    }

    // ------------------------------------------------------------------ queries

    private long liveExtra(UUID uuid) {
        Live l = live.get(uuid);
        if (l == null) return 0L;
        synchronized (l) {
            return l.paused ? 0L : Math.max(0, System.currentTimeMillis() - l.lastTick);
        }
    }

    /** All-time counted milliseconds, live session included. */
    public long getTotal(UUID uuid) {
        return get(uuid, Period.ALL);
    }

    public long get(UUID uuid, Period period) {
        PlayerStats s = cache.get(uuid);
        if (s != null) {
            long extra = liveExtra(uuid);
            if (period == Period.ALL) return s.getTotal() + extra;
            return rangeSum(s, period) + extra;
        }
        Integer idx = boardRanks.get(period).get(uuid);
        if (idx != null) return board.get(period).get(idx - 1).getMs();
        return 0L;
    }

    public int getSessions(UUID uuid) {
        PlayerStats s = cache.get(uuid);
        return s == null ? 0 : s.getSessions();
    }

    public long getLongest(UUID uuid) {
        PlayerStats s = cache.get(uuid);
        long v = s == null ? 0 : s.getLongest();
        return Math.max(v, sessionMs(uuid));
    }

    public long getLastEnd(UUID uuid) {
        PlayerStats s = cache.get(uuid);
        return s == null ? 0 : s.getLastEnd();
    }

    public long rangeSum(PlayerStats s, Period period) {
        if (period == Period.ALL) return s.getTotal();
        String[] range = range(period, System.currentTimeMillis());
        return s.sumDays(range[0], range[1]);
    }

    public List<Row> top(Period period) {
        return board.get(period);
    }

    /** 1-based rank, 0 when unranked. */
    public int rank(UUID uuid, Period period) {
        Integer r = boardRanks.get(period).get(uuid);
        return r == null ? 0 : r;
    }

    public Row row(Period period, int rank) {
        List<Row> rows = board.get(period);
        return rank >= 1 && rank <= rows.size() ? rows.get(rank - 1) : null;
    }

    public long boardAge() {
        return boardTime == 0 ? -1 : System.currentTimeMillis() - boardTime;
    }

    // ------------------------------------------------------------------ claims (milestones)

    public boolean hasClaim(UUID uuid, String key) {
        PlayerStats s = cache.get(uuid);
        return s != null && s.hasClaim(key);
    }

    /** @return {@code true} if the claim was new (the reward may be paid) */
    public boolean claim(UUID uuid, String key) {
        PlayerStats s = cache.get(uuid);
        return s != null && s.addClaim(key);
    }

    // ------------------------------------------------------------------ time helpers

    public ZoneId zone() {
        return TimeUtil.zone(plugin.getConfig().getString("playtime.timezone", "SYSTEM"));
    }

    public DayOfWeek weekStart() {
        return TimeUtil.parseDay(plugin.getConfig().getString("playtime.week-start", "MONDAY"), DayOfWeek.MONDAY);
    }

    public String dayKey(long now) {
        return TimeUtil.dayKey(java.time.Instant.ofEpochMilli(now).atZone(zone()).toLocalDate());
    }

    public LocalDate today() {
        return java.time.Instant.now().atZone(zone()).toLocalDate();
    }

    /** {from, to} ISO day keys, inclusive. */
    public String[] range(Period period, long now) {
        LocalDate today = java.time.Instant.ofEpochMilli(now).atZone(zone()).toLocalDate();
        switch (period) {
            case TODAY:
                return new String[]{TimeUtil.dayKey(today), TimeUtil.dayKey(today)};
            case WEEK:
                return new String[]{TimeUtil.dayKey(TimeUtil.startOfWeek(today, weekStart())), TimeUtil.dayKey(today)};
            case MONTH:
                return new String[]{TimeUtil.dayKey(today.withDayOfMonth(1)), TimeUtil.dayKey(today)};
            default:
                return new String[]{"0000-01-01", "9999-12-31"};
        }
    }

    /** Identifier of the current week / day / month, used as part of milestone claim keys. */
    public String periodKey(Period period, long now) {
        LocalDate today = java.time.Instant.ofEpochMilli(now).atZone(zone()).toLocalDate();
        switch (period) {
            case TODAY: return TimeUtil.dayKey(today);
            case WEEK: return TimeUtil.dayKey(TimeUtil.startOfWeek(today, weekStart()));
            case MONTH: return today.getYear() + "-" + String.format("%02d", today.getMonthValue());
            default: return "all";
        }
    }

    // ------------------------------------------------------------------ persistence

    /** Queues all pending changes of every cached player. */
    public void flushAll() {
        for (Map.Entry<UUID, PlayerStats> e : cache.entrySet()) flush(e.getKey(), e.getValue());
    }

    public void flush(UUID uuid, PlayerStats s) {
        final StatsDelta d = s.drain();
        if (d == null) return;
        plugin.getData().executor().execute("save duty statistics of " + uuid, () -> {
            try {
                plugin.getData().stats().apply(d);
            } catch (Exception ex) {
                cache.putIfAbsent(uuid, s);
                s.restore(d);
                throw new RuntimeException(ex);
            }
        });
    }

    /** Resets playtime (claims are kept so rewards cannot be earned twice). */
    public void reset(UUID uuid) {
        PlayerStats s = cache.get(uuid);
        Live l = live.get(uuid);
        if (l != null) {
            synchronized (l) {
                l.activeMs = 0;
                l.lastTick = System.currentTimeMillis();
            }
        }
        if (s != null) {
            s.reset();
            flush(uuid, s);
        } else {
            final StatsDelta d = new StatsDelta(uuid, null, true, 0, 0, 0, 0,
                    new HashMap<String, Long>(), new HashSet<String>());
            plugin.getData().executor().execute("reset duty statistics of " + uuid,
                    () -> {
                        try {
                            plugin.getData().stats().apply(d);
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    });
        }
        refreshBoard();
    }

    // ------------------------------------------------------------------ leaderboard cache

    public void refreshBoard() {
        flushAll();
        final String since = earliestKey();
        final int keepDays = plugin.getConfig().getInt("playtime.keep-daily-days", 120);
        final Map<UUID, PlayerStats> mem = new HashMap<>(cache);
        final Map<Period, String[]> ranges = new EnumMap<>(Period.class);
        long now = System.currentTimeMillis();
        for (Period p : Period.values()) ranges.put(p, range(p, now));
        final String pruneBefore = TimeUtil.dayKey(today().minusDays(Math.max(keepDays, 35)));

        plugin.getData().executor().execute("refresh leaderboard", () -> {
            try {
                Map<UUID, PlayerStats> all = plugin.getData().stats().loadAll(since);
                all.putAll(mem); // in-memory values are fresher than the stored ones
                build(all, ranges);
                plugin.getData().stats().prune(pruneBefore);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Could not refresh the playtime leaderboard: " + ex.getMessage());
            }
        });
    }

    private String earliestKey() {
        LocalDate t = today();
        LocalDate week = TimeUtil.startOfWeek(t, weekStart());
        LocalDate month = t.withDayOfMonth(1);
        return TimeUtil.dayKey(week.isBefore(month) ? week : month);
    }

    private void build(Map<UUID, PlayerStats> all, Map<Period, String[]> ranges) {
        Map<Period, List<Row>> newBoard = new EnumMap<>(Period.class);
        Map<Period, Map<UUID, Integer>> newRanks = new EnumMap<>(Period.class);

        for (Period p : Period.values()) {
            List<Row> rows = new ArrayList<>();
            String[] r = ranges.get(p);
            for (Map.Entry<UUID, PlayerStats> e : all.entrySet()) {
                PlayerStats s = e.getValue();
                long ms = p == Period.ALL ? s.getTotal() : s.sumDays(r[0], r[1]);
                if (ms > 0) rows.add(new Row(e.getKey(), s.getName(), ms));
            }
            rows.sort((a, b) -> Long.compare(b.ms, a.ms));
            Map<UUID, Integer> ranks = new HashMap<>();
            for (int i = 0; i < rows.size(); i++) ranks.put(rows.get(i).uuid, i + 1);
            newBoard.put(p, Collections.unmodifiableList(rows));
            newRanks.put(p, ranks);
        }
        boardRanks = newRanks;
        board = newBoard;
        boardTime = System.currentTimeMillis();
    }
}
