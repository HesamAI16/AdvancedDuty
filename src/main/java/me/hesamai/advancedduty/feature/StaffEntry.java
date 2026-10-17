package me.hesamai.advancedduty.feature;

import java.util.UUID;

/** One staff member as shown in the staff list / Discord (local or from another server). */
public final class StaffEntry {

    private final UUID uuid;
    private final String name;
    private final String server;
    private final boolean onDuty;
    private final long sessionMs;
    private final boolean afk;
    private final boolean vanished;

    public StaffEntry(UUID uuid, String name, String server, boolean onDuty, long sessionMs, boolean afk, boolean vanished) {
        this.uuid = uuid;
        this.name = name;
        this.server = server;
        this.onDuty = onDuty;
        this.sessionMs = sessionMs;
        this.afk = afk;
        this.vanished = vanished;
    }

    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public String getServer() { return server; }
    public boolean isOnDuty() { return onDuty; }
    public long getSessionMs() { return sessionMs; }
    public boolean isAfk() { return afk; }
    public boolean isVanished() { return vanished; }
}
