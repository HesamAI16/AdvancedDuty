package me.hesamai.advancedduty.duty;

import me.hesamai.advancedduty.duty.inventory.DualInventoryProfile;

public class DutyStateSnapshot {

    private final DualInventoryProfile profile;
    private final DutySession session;
    private final DutyMode mode;
    private final long totalPlaytimeMs; // NEW

    public DutyStateSnapshot(DualInventoryProfile profile, DutySession session, DutyMode mode, long totalPlaytimeMs) {
        this.profile = profile;
        this.session = session;
        this.mode = mode;
        this.totalPlaytimeMs = totalPlaytimeMs;
    }

    public DutyStateSnapshot(DualInventoryProfile profile, DutySession session, DutyMode mode) {
        this(profile, session, mode, 0L);
    }

    public DualInventoryProfile getProfile() { return profile; }
    public DutySession getSession() { return session; }
    public DutyMode getMode() { return mode; }
    public long getTotalPlaytimeMs() { return totalPlaytimeMs; }
}