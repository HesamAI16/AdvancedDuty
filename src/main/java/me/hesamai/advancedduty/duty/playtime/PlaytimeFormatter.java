package me.hesamai.advancedduty.duty.playtime;

/**
 * Formats a duration in milliseconds as "3d 4h 5m" / "3h 22m 10s" / "5m 10s" / "10s".
 * The unit suffixes can be customised from the language file ({@code placeholders.time.*}).
 */
public final class PlaytimeFormatter {

    private static volatile String day = "d";
    private static volatile String hour = "h";
    private static volatile String minute = "m";
    private static volatile String second = "s";

    private PlaytimeFormatter() {
    }

    public static void setUnits(String d, String h, String m, String s) {
        day = d == null || d.isEmpty() ? "d" : d;
        hour = h == null || h.isEmpty() ? "h" : h;
        minute = m == null || m.isEmpty() ? "m" : m;
        second = s == null || s.isEmpty() ? "s" : s;
    }

    public static String format(long ms) {
        long totalSeconds = Math.max(0L, ms) / 1000;
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (days > 0) return days + day + " " + hours + hour + " " + minutes + minute;
        if (hours > 0) return hours + hour + " " + minutes + minute + " " + seconds + second;
        if (minutes > 0) return minutes + minute + " " + seconds + second;
        return seconds + second;
    }

    /** Like {@link #format(long)} but always shows seconds and never drops them for long durations. */
    public static String formatFull(long ms) {
        long totalSeconds = Math.max(0L, ms) / 1000;
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append(day).append(' ');
        if (days > 0 || hours > 0) sb.append(hours).append(hour).append(' ');
        if (days > 0 || hours > 0 || minutes > 0) sb.append(minutes).append(minute).append(' ');
        sb.append(seconds).append(second);
        return sb.toString();
    }
}
