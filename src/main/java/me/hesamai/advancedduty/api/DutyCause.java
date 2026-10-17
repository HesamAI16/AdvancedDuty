package me.hesamai.advancedduty.api;

/** Why a duty state changed. */
public enum DutyCause {
    /** The player (or an admin) used a command. */
    COMMAND,
    /** An admin changed the state of another player. */
    ADMIN,
    /** Automatic: on join / auto-duty permission. */
    AUTO,
    /** The AFK system took the player off duty. */
    AFK,
    /** The player disconnected. */
    QUIT,
    /** The server is stopping. */
    SHUTDOWN,
    /** A staff tool (e.g. the duty item) was used. */
    TOOL,
    /** Another plugin used the API. */
    API,
    /** Duty was restored after a reconnect. */
    JOIN,
    /** Moved into a world where duty is not allowed. */
    WORLD;

    /** Causes that can never be vetoed by an event or a cooldown. */
    public boolean isForced() {
        return this == QUIT || this == SHUTDOWN || this == AFK || this == WORLD || this == JOIN;
    }
}
