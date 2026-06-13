package me.hesamai.advancedduty.duty.playtime;

public final class PlaytimeFormatter {

    private PlaytimeFormatter() {}

    public static String format(long ms) {
        long totalSeconds = ms / 1000;
        long days    = totalSeconds / 86400;
        long hours   = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (days > 0)    return days + "d " + hours + "h " + minutes + "m";
        if (hours > 0)   return hours + "h " + minutes + "m " + seconds + "s";
        if (minutes > 0) return minutes + "m " + seconds + "s";
        return seconds + "s";
    }
}