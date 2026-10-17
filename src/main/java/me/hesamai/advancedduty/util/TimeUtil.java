package me.hesamai.advancedduty.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small collection of time helpers: duration parsing ("1d 2h 30m"), clock formatting and the
 * day / week / month keys used by the playtime buckets and milestones.
 */
public final class TimeUtil {

    private static final Pattern DURATION_TOKEN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*([a-zA-Z]*)");
    public static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT);

    private TimeUtil() {
    }

    /**
     * Parses durations such as {@code 90m}, {@code 5h}, {@code 1d 12h}, {@code 1h30m15s}, {@code 2w}.
     * A bare number is interpreted as <b>minutes</b>. Returns {@code -1} when the text cannot be parsed.
     */
    public static long parseDuration(String text) {
        if (text == null) return -1L;
        String s = text.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return -1L;

        Matcher m = DURATION_TOKEN.matcher(s);
        double totalMs = 0;
        int end = 0;
        boolean any = false;
        while (m.find()) {
            // everything between tokens must be whitespace / separators
            String gap = s.substring(end, m.start()).trim();
            if (!gap.isEmpty() && !gap.equals(",") && !gap.equals("and")) return -1L;
            end = m.end();

            double value;
            try {
                value = Double.parseDouble(m.group(1));
            } catch (NumberFormatException ex) {
                return -1L;
            }
            long unit = unitMillis(m.group(2));
            if (unit < 0) return -1L;
            totalMs += value * unit;
            any = true;
        }
        if (!any || !s.substring(end).trim().isEmpty()) return -1L;
        return (long) totalMs;
    }

    private static long unitMillis(String unit) {
        switch (unit) {
            case "":
            case "m":
            case "min":
            case "mins":
            case "minute":
            case "minutes":
                return 60_000L;
            case "s":
            case "sec":
            case "secs":
            case "second":
            case "seconds":
                return 1_000L;
            case "h":
            case "hr":
            case "hrs":
            case "hour":
            case "hours":
                return 3_600_000L;
            case "d":
            case "day":
            case "days":
                return 86_400_000L;
            case "w":
            case "wk":
            case "week":
            case "weeks":
                return 7 * 86_400_000L;
            default:
                return -1L;
        }
    }

    /** {@code HH:MM:SS} (hours may exceed 24). */
    public static String clock(long ms) {
        long totalSeconds = Math.max(0, ms) / 1000L;
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", h, m, s);
    }

    public static ZoneId zone(String configured) {
        if (configured == null || configured.trim().isEmpty() || configured.equalsIgnoreCase("system")) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(configured.trim());
        } catch (Exception ex) {
            return ZoneId.systemDefault();
        }
    }

    public static String dayKey(LocalDate date) {
        return DAY_FORMAT.format(date);
    }

    public static LocalDate startOfWeek(LocalDate date, DayOfWeek firstDay) {
        return date.with(TemporalAdjusters.previousOrSame(firstDay));
    }

    public static DayOfWeek parseDay(String raw, DayOfWeek def) {
        if (raw == null) return def;
        try {
            return DayOfWeek.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return def;
        }
    }

    public static String formatDate(long epochMs, String pattern, ZoneId zone) {
        try {
            return DateTimeFormatter.ofPattern(pattern, Locale.ROOT).withZone(zone)
                    .format(java.time.Instant.ofEpochMilli(epochMs));
        } catch (Exception ex) {
            return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT).withZone(zone)
                    .format(java.time.Instant.ofEpochMilli(epochMs));
        }
    }
}
