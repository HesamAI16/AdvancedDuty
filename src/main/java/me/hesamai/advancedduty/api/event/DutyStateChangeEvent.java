package me.hesamai.advancedduty.api.event;

import me.hesamai.advancedduty.api.DutyCause;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Fired <b>after</b> a player went on or off duty. */
public class DutyStateChangeEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final boolean onDuty;
    private final String reason;
    private final DutyCause cause;
    private final long sessionMillis;

    public DutyStateChangeEvent(@NotNull Player player, boolean onDuty, @Nullable String reason,
                                @NotNull DutyCause cause, long sessionMillis) {
        super(player);
        this.onDuty = onDuty;
        this.reason = reason;
        this.cause = cause;
        this.sessionMillis = sessionMillis;
    }

    /** The state the player is in now. */
    public boolean isOnDuty() { return onDuty; }

    @Nullable public String getReason() { return reason; }

    @NotNull public DutyCause getCause() { return cause; }

    /** Counted playtime of the session that just ended (0 when going on duty). */
    public long getSessionMillis() { return sessionMillis; }

    @NotNull @Override
    public HandlerList getHandlers() { return HANDLERS; }

    @NotNull
    public static HandlerList getHandlerList() { return HANDLERS; }
}
